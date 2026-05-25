package me.lucaperri.dev.languages.references

import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar

// References are provided entirely by NasmLabelRefMixin (findReferenceAt /
// getReference / getReferences overrides), so no providers are registered here.
// The extension point entry in plugin.xml is kept as a no-op placeholder.
class NasmReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) = Unit
}
