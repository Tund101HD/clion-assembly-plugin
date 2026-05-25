package me.lucaperri.dev.languages.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.psi.NasmLabelDef
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmTypes

abstract class NasmLabelDefMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), NasmNamedElement {

    override fun getName(): String? = nameIdentifier?.text

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(NasmTypes.IDENTIFIER)?.psi

    override fun setName(name: String): PsiElement {
        val current = nameIdentifier ?: return this
        val dummy = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.nasm", NasmFileType.INSTANCE, "$name:\n")
        val replacement = PsiTreeUtil.findChildOfType(dummy, NasmLabelDef::class.java)
            ?.nameIdentifier ?: return this
        current.replace(replacement)
        return this
    }
}
