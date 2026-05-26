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

class NasmLabelReference(element: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(element, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val name = value
        val project = element.project
        val containing = element.containingFile as? NasmFile

        // NASM local labels (`.foo`, but not `..foo` which is reserved for special
        // names) are scoped to the most recent non-local label above them. Resolve
        // only within that scope and never cross-file.
        if (name.isNasmLocalLabel()) {
            return containing?.let { findLocalLabelInScope(name, element, it) }
        }

        // Globals: prefer the current file (matches the assembler's "local first" lookup).
        containing?.let { file ->
            findGlobalNamedElement(file, name)?.let { return it }
        }

        val manager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        for (vf in FileTypeIndex.getFiles(NasmFileType.INSTANCE, scope)) {
            val file = manager.findFile(vf) as? NasmFile ?: continue
            if (file === containing) continue
            findGlobalNamedElement(file, name)?.let { return it }
        }
        return null
    }

    private fun findGlobalNamedElement(file: NasmFile, name: String): NasmNamedElement? =
        PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
            .firstOrNull { def ->
                def.name == name && !(def is NasmLabelDef && def.name?.isNasmLocalLabel() == true)
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
            // Globals from the current file.
            PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java)
                .mapNotNull { it.name }
                .filter { !it.isNasmLocalLabel() }
                .forEach { result.add(LookupElementBuilder.create(it).withTypeText("label")) }

            // Local labels: only those in the reference's current scope.
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

        val manager = PsiManager.getInstance(project)
        FileTypeIndex.getFiles(NasmFileType.INSTANCE, GlobalSearchScope.allScope(project))
            .filter { vf -> containing?.virtualFile?.let { it != vf } != false }
            .forEach { vf ->
                val file = manager.findFile(vf) as? NasmFile ?: return@forEach
                PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java)
                    .mapNotNull { it.name }
                    .filter { !it.isNasmLocalLabel() }
                    .forEach { result.add(LookupElementBuilder.create(it).withTypeText(vf.name)) }
            }

        return result.toTypedArray()
    }
}

private fun String.isNasmLocalLabel(): Boolean = startsWith(".") && !startsWith("..")
