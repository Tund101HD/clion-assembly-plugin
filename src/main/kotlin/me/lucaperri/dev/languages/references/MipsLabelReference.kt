package me.lucaperri.dev.languages.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.psi.MipsFile
import me.lucaperri.dev.languages.psi.MipsLabelRef
import me.lucaperri.dev.languages.psi.MipsLocalLabelRef
import me.lucaperri.dev.languages.psi.MipsNamedElement
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsLabelReference(element: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(element, range, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val name = value
        val project = element.project
        val containing = element.containingFile as? MipsFile

        containing?.let { file ->
            PsiTreeUtil.findChildrenOfType(file, MipsNamedElement::class.java)
                .firstOrNull { it.name == name }
                ?.let { return it }
        }

        val manager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        for (vf in FileTypeIndex.getFiles(MipsFileType.INSTANCE, scope)) {
            val file = manager.findFile(vf) as? MipsFile ?: continue
            if (file === containing) continue
            PsiTreeUtil.findChildrenOfType(file, MipsNamedElement::class.java)
                .firstOrNull { it.name == name }
                ?.let { return it }
        }
        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identChild = element.node.findChildByType(MipsTypes.IDENTIFIER)?.psi
        if (identChild != null) {
            // label_ref: replace the IDENTIFIER leaf
            val dummy = PsiFileFactory.getInstance(element.project)
                .createFileFromText("dummy.mips", MipsFileType.INSTANCE, "jal $newElementName\n")
            val newIdent = PsiTreeUtil.findChildOfType(dummy, MipsLabelRef::class.java)
                ?.node?.findChildByType(MipsTypes.IDENTIFIER)?.psi ?: return element
            identChild.replace(newIdent)
            return element
        }
        val dirChild = element.node.findChildByType(MipsTypes.DIRECTIVE)?.psi ?: return element
        // local_label_ref: replace the DIRECTIVE leaf
        val dummy = PsiFileFactory.getInstance(element.project)
            .createFileFromText("dummy.mips", MipsFileType.INSTANCE, "j $newElementName\n")
        val newDir = PsiTreeUtil.findChildOfType(dummy, MipsLocalLabelRef::class.java)
            ?.node?.findChildByType(MipsTypes.DIRECTIVE)?.psi ?: return element
        dirChild.replace(newDir)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
