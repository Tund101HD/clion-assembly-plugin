package me.lucaperri.dev.languages.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import me.lucaperri.dev.languages.lexer.MipsLexerAdapter
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = MipsLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        MipsTypes.COMMENT    -> AsmColors.pack(AsmColors.COMMENT)
        MipsTypes.STRING     -> AsmColors.pack(AsmColors.STRING)
        MipsTypes.NUMBER     -> AsmColors.pack(AsmColors.NUMBER)
        MipsTypes.DIRECTIVE  -> AsmColors.pack(AsmColors.DIRECTIVE)
        MipsTypes.LPAREN,
        MipsTypes.RPAREN     -> AsmColors.pack(AsmColors.PARENTHESES)
        MipsTypes.COMMA      -> AsmColors.pack(AsmColors.COMMA)
        MipsTypes.PLUS,
        MipsTypes.MINUS      -> AsmColors.pack(AsmColors.OPERATOR)
        MipsTypes.IDENTIFIER -> AsmColors.pack(AsmColors.IDENTIFIER)
        TokenType.BAD_CHARACTER -> AsmColors.pack(HighlighterColors.BAD_CHARACTER)
        else -> AsmColors.EMPTY
    }
}

class MipsSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        MipsSyntaxHighlighter()
}
