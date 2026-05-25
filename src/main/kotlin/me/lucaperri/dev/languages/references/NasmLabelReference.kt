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

        // Prefer a definition in the current file (matches the assembler's "local first" lookup).
        containing?.let { file ->
            PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
                .firstOrNull { it.name == name }
                ?.let { return it }
        }

        val manager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        for (vf in FileTypeIndex.getFiles(NasmFileType.INSTANCE, scope)) {
            val file = manager.findFile(vf) as? NasmFile ?: continue
            if (file === containing) continue
            PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
                .firstOrNull { it.name == name }
                ?.let { return it }
        }
        return null
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
            PsiTreeUtil.findChildrenOfType(file, NasmLabelDef::class.java)
                .mapNotNull { it.name }
                .forEach { result.add(LookupElementBuilder.create(it).withTypeText("label")) }

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
                    .forEach { result.add(LookupElementBuilder.create(it).withTypeText(vf.name)) }
            }

        return result.toTypedArray()
    }
}
