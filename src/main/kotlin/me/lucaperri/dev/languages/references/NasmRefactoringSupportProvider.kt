package me.lucaperri.dev.languages.references

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.psi.NasmNamedElement

class NasmRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean =
        element is NasmNamedElement
}
