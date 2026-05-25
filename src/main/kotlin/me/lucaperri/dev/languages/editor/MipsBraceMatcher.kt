package me.lucaperri.dev.languages.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsBraceMatcher : PairedBraceMatcher {

    private val pairs = arrayOf(
        BracePair(MipsTypes.LPAREN, MipsTypes.RPAREN, false)
    )

    override fun getPairs(): Array<BracePair> = pairs

    override fun isPairedBracesAllowedBeforeType(
        lbraceType: IElementType,
        contextType: IElementType?
    ): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
