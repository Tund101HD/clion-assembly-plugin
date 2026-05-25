package me.lucaperri.dev.languages.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.psi.MipsNamedElement
import me.lucaperri.dev.languages.psi.MipsTypes

abstract class MipsLabelDefMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), MipsNamedElement {

    override fun getName(): String? = nameIdentifier?.text

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(MipsTypes.IDENTIFIER)?.psi
            ?: node.findChildByType(MipsTypes.DIRECTIVE)?.psi

    override fun setName(name: String): PsiElement {
        val current = nameIdentifier ?: return this
        val dummy = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.mips", MipsFileType.INSTANCE, "$name:\n")
        val replacement = PsiTreeUtil.findChildOfType(dummy, MipsNamedElement::class.java)
            ?.nameIdentifier ?: return this
        current.replace(replacement)
        return this
    }
}
