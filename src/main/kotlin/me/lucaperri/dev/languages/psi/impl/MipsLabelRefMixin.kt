package me.lucaperri.dev.languages.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import me.lucaperri.dev.languages.references.MipsLabelReference

abstract class MipsLabelRefMixin(node: ASTNode) : ASTWrapperPsiElement(node) {

    // MIPS registers ($t0, $a0, …) are IDENTIFIER tokens that land in label_ref
    // position when used as instruction operands. The $ prefix distinguishes them
    // from label names so they can be excluded without a register-table lookup.

    override fun findReferenceAt(offset: Int): PsiReference? {
        if (text.startsWith("$")) return null
        return MipsLabelReference(this, TextRange(0, textLength))
    }

    override fun getReference(): PsiReference? {
        if (text.startsWith("$")) return null
        return MipsLabelReference(this, TextRange(0, textLength))
    }

    override fun getReferences(): Array<PsiReference> {
        if (text.startsWith("$")) return PsiReference.EMPTY_ARRAY
        return arrayOf(MipsLabelReference(this, TextRange(0, textLength)))
    }
}
