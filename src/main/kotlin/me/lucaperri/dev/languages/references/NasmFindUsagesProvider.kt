package me.lucaperri.dev.languages.references

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import me.lucaperri.dev.languages.lexer.NasmLexerAdapter
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        NasmLexerAdapter(),
        TokenSet.create(NasmTypes.IDENTIFIER),
        TokenSet.create(NasmTypes.COMMENT),
        TokenSet.create(NasmTypes.STRING)
    )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is NasmNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = if (element is NasmNamedElement) "label" else ""

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? NasmNamedElement)?.name ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? NasmNamedElement)?.name ?: element.text
}
