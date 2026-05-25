package me.lucaperri.dev.languages.references

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import me.lucaperri.dev.languages.lexer.MipsLexerAdapter
import me.lucaperri.dev.languages.psi.MipsNamedElement
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        MipsLexerAdapter(),
        TokenSet.create(MipsTypes.IDENTIFIER),
        TokenSet.create(MipsTypes.COMMENT),
        TokenSet.create(MipsTypes.STRING)
    )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is MipsNamedElement

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = if (element is MipsNamedElement) "label" else ""

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? MipsNamedElement)?.name ?: ""

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? MipsNamedElement)?.name ?: element.text
}
