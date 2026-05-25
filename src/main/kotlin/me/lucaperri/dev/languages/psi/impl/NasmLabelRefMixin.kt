package me.lucaperri.dev.languages.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import me.lucaperri.dev.languages.references.NasmLabelReference

abstract class NasmLabelRefMixin(node: ASTNode) : ASTWrapperPsiElement(node) {

    // ASTWrapperPsiElement.findReferenceAt descends into the IDENTIFIER leaf child,
    // which returns null because LeafPsiElement.getReferences() does not consult
    // PsiReferenceService. Overriding here short-circuits that descent so that
    // PsiFile.findReferenceAt (used by GotoDeclarationAction and ReferencesSearch)
    // returns the reference directly without reaching the leaf.
    override fun findReferenceAt(offset: Int): PsiReference? =
        NasmLabelReference(this, TextRange(0, textLength))

    override fun getReference(): PsiReference? =
        NasmLabelReference(this, TextRange(0, textLength))

    // Override getReferences so ReferencesSearch (Find Usages, Rename) finds the
    // reference when walking PSI elements and calling element.getReferences().
    override fun getReferences(): Array<PsiReference> =
        arrayOf(NasmLabelReference(this, TextRange(0, textLength)))
}
