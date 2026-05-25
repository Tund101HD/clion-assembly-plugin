package me.lucaperri.dev.languages.references

import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar

// References are provided entirely by MipsLabelRefMixin. See NasmReferenceContributor.
class MipsReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) = Unit
}
