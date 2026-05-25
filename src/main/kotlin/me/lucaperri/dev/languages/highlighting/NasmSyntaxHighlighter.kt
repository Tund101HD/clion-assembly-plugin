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
import me.lucaperri.dev.languages.lexer.NasmLexerAdapter
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = NasmLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
        NasmTypes.COMMENT    -> AsmColors.pack(AsmColors.COMMENT)
        NasmTypes.STRING     -> AsmColors.pack(AsmColors.STRING)
        NasmTypes.NUMBER,
        NasmTypes.DOLLAR,
        NasmTypes.DOLLAR_DOLLAR -> AsmColors.pack(AsmColors.NUMBER)
        NasmTypes.DIRECTIVE  -> AsmColors.pack(AsmColors.DIRECTIVE)
        NasmTypes.SECTION_KW,
        NasmTypes.SEGMENT_KW,
        NasmTypes.GLOBAL_KW,
        NasmTypes.EXTERN_KW,
        NasmTypes.EQU_KW,
        NasmTypes.TIMES_KW,
        NasmTypes.DATA_KW    -> AsmColors.pack(AsmColors.KEYWORD)
        NasmTypes.SIZE_SPEC  -> AsmColors.pack(AsmColors.SIZE_SPEC)
        NasmTypes.LBRACKET,
        NasmTypes.RBRACKET   -> AsmColors.pack(AsmColors.BRACKETS)
        NasmTypes.COMMA      -> AsmColors.pack(AsmColors.COMMA)
        NasmTypes.PLUS,
        NasmTypes.MINUS,
        NasmTypes.STAR       -> AsmColors.pack(AsmColors.OPERATOR)
        NasmTypes.IDENTIFIER -> AsmColors.pack(AsmColors.IDENTIFIER)
        TokenType.BAD_CHARACTER -> AsmColors.pack(HighlighterColors.BAD_CHARACTER)
        else -> AsmColors.EMPTY
    }
}

class NasmSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        NasmSyntaxHighlighter()
}
