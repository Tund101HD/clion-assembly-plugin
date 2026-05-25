package me.lucaperri.dev.languages.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object AsmColors {
    val INSTRUCTION  = createTextAttributesKey("ASM_INSTRUCTION",  DefaultLanguageHighlighterColors.KEYWORD)
    val REGISTER     = createTextAttributesKey("ASM_REGISTER",     DefaultLanguageHighlighterColors.STATIC_FIELD)
    val LABEL_DEF    = createTextAttributesKey("ASM_LABEL_DEF",    DefaultLanguageHighlighterColors.LABEL)
    val LABEL_REF    = createTextAttributesKey("ASM_LABEL_REF",    DefaultLanguageHighlighterColors.IDENTIFIER)
    val DIRECTIVE    = createTextAttributesKey("ASM_DIRECTIVE",    DefaultLanguageHighlighterColors.METADATA)
    val KEYWORD      = createTextAttributesKey("ASM_KEYWORD",      DefaultLanguageHighlighterColors.KEYWORD)
    val SIZE_SPEC    = createTextAttributesKey("ASM_SIZE_SPEC",    DefaultLanguageHighlighterColors.METADATA)
    val NUMBER       = createTextAttributesKey("ASM_NUMBER",       DefaultLanguageHighlighterColors.NUMBER)
    val STRING       = createTextAttributesKey("ASM_STRING",       DefaultLanguageHighlighterColors.STRING)
    val COMMENT      = createTextAttributesKey("ASM_COMMENT",      DefaultLanguageHighlighterColors.LINE_COMMENT)
    val IDENTIFIER   = createTextAttributesKey("ASM_IDENTIFIER",   DefaultLanguageHighlighterColors.IDENTIFIER)
    val BRACKETS     = createTextAttributesKey("ASM_BRACKETS",     DefaultLanguageHighlighterColors.BRACKETS)
    val PARENTHESES  = createTextAttributesKey("ASM_PARENTHESES",  DefaultLanguageHighlighterColors.PARENTHESES)
    val COMMA        = createTextAttributesKey("ASM_COMMA",        DefaultLanguageHighlighterColors.COMMA)
    val OPERATOR     = createTextAttributesKey("ASM_OPERATOR",     DefaultLanguageHighlighterColors.OPERATION_SIGN)

    fun pack(key: TextAttributesKey): Array<TextAttributesKey> = arrayOf(key)
    val EMPTY: Array<TextAttributesKey> = TextAttributesKey.EMPTY_ARRAY
}
