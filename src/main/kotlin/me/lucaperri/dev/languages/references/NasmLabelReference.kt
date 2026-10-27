package me.lucaperri.dev.languages.references

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.psi.NasmExternStmt
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmLabelDef
import me.lucaperri.dev.languages.psi.NasmLabelRef
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmTypes
import me.lucaperri.dev.languages.psi.findNonLocalDefinition
import me.lucaperri.dev.languages.psi.foreignNasmDefinitions
import me.lucaperri.dev.languages.psi.globalNames
import me.lucaperri.dev.languages.psi.isNasmLocalLabel
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

class NasmLabelReference(element: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(element, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val name = value
        val containing = element.containingFile as? NasmFile

        // NASM local labels (`.foo`, but not `..foo` which is reserved for special
        // names) are scoped to the most recent non-local label above them. Resolve
        // only within that scope and never cross-file.
        if (name.isNasmLocalLabel()) {
            return containing?.let { findLocalLabelInScope(name, element, it) }
        }

        // Globals: prefer the current file (matches the assembler's "local first" lookup).
        val file = containing ?: return null
        file.findNonLocalDefinition(name)?.let { return it }

        // Other files: a `global` definition wins. One that isn't exported is only
        // reachable while the user hasn't opted into hiding it; NasmExternInspection
        // reports such a reference either way.
        val hideNonGlobal = AsmExecutableSettings.getInstance().hideNonGlobalLabels
        return foreignNasmDefinitions(file, name)
            .firstOrNull { it.isGlobal || !hideNonGlobal }
            ?.element
    }

    private fun findLocalLabelInScope(name: String, refElement: PsiElement, file: NasmFile): NasmLabelDef? {
        val (start, end) = currentScopeBounds(file, refElement) ?: return null
        return PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java)
            .firstOrNull { def ->
                val o = def.textRange.startOffset
                def.name == name && o in start until end
            }
    }

    private fun currentScopeBounds(file: NasmFile, refElement: PsiElement): Pair<Int, Int>? {
        val refOffset = refElement.textRange.startOffset
        val labels = PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java).toList()
        val parentIdx = labels.indexOfLast { def ->
            def.textRange.startOffset < refOffset && def.name?.isNasmLocalLabel() == false
        }
        if (parentIdx < 0) return null
        val start = labels[parentIdx].textRange.startOffset
        val nextIdx = (parentIdx + 1 until labels.size).firstOrNull {
            labels[it].name?.isNasmLocalLabel() == false
        } ?: labels.size
        val end = if (nextIdx < labels.size) labels[nextIdx].textRange.startOffset else Int.MAX_VALUE
        return start to end
    }

    // element is a NasmLabelRef composite; no ElementManipulator is registered for it,
    // so we handle rename by replacing the child IDENTIFIER leaf directly.
    override fun handleElementRename(newElementName: String): PsiElement {
        val identifier = element.node.findChildByType(NasmTypes.IDENTIFIER)?.psi
            ?: return element
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("dummy.nasm", NasmFileType.INSTANCE, "call $newElementName\n")
        val newRef = PsiTreeUtil.findChildOfType(dummy, NasmLabelRef::class.java)
            ?: return element
        val newIdentifier = newRef.node.findChildByType(NasmTypes.IDENTIFIER)?.psi
            ?: return element
        identifier.replace(newIdentifier)
        return element
    }

    override fun getVariants(): Array<Any> {
        val result = mutableListOf<Any>()
        val project = element.project
        val containing = element.containingFile as? NasmFile

        containing?.let { file ->
            // Globals from the current file — anything that implements NasmNamedElement
            // (regular labels `foo:`, data labels `msg db ...`, equ labels `MAX equ ...`).
            // Local labels (.foo) are scoped separately below.
            PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
                .mapNotNull { def -> def.name?.let { it to def } }
                .filter { (name, _) -> !name.isNasmLocalLabel() }
                .forEach { (name, def) ->
                    result.add(LookupElementBuilder.create(name).withTypeText(labelKind(def)))
                }

            // Local labels apply only to NasmLabelDef (the `foo:` colon form); NASM
            // doesn't allow `.foo db ...` as a local data definition.
            currentScopeBounds(file, element)?.let { (start, end) ->
                PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java)
                    .filter { def ->
                        val o = def.textRange.startOffset
                        def.name?.isNasmLocalLabel() == true && o in start until end
                    }
                    .mapNotNull { it.name }
                    .forEach { result.add(LookupElementBuilder.create(it).withTypeText("local label")) }
            }

            PsiTreeUtil.findChildrenOfType(file, NasmExternStmt::class.java)
                .flatMap { it.labelRefList }
                .mapNotNull { it.node.findChildByType(NasmTypes.IDENTIFIER)?.text }
                .forEach { result.add(LookupElementBuilder.create(it).withTypeText("extern")) }
        }

        val hideNonGlobal = AsmExecutableSettings.getInstance().hideNonGlobalLabels
        val originVf = containing?.originalFile?.virtualFile
        val manager = PsiManager.getInstance(project)
        FileTypeIndex.getFiles(NasmFileType.INSTANCE, GlobalSearchScope.allScope(project))
            .filter { vf -> vf != originVf }
            .forEach { vf ->
                val file = manager.findFile(vf) as? NasmFile ?: return@forEach
                val globals = file.globalNames()
                PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
                    .mapNotNull { it.name }
                    .filter { !it.isNasmLocalLabel() }
                    .forEach { name ->
                        val isGlobal = name in globals
                        if (!isGlobal && hideNonGlobal) return@forEach
                        val typeText = if (isGlobal) vf.name else "${vf.name} (not global)"
                        result.add(LookupElementBuilder.create(name).withTypeText(typeText))
                    }
            }

        return result.toTypedArray()
    }

    private fun labelKind(def: NasmNamedElement): String = when (def) {
        is NasmLabelDef -> "label"
        else -> "symbol"
    }
}
