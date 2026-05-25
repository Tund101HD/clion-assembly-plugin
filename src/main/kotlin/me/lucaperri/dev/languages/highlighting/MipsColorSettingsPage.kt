package me.lucaperri.dev.languages.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import me.lucaperri.dev.languages.MipsLanguage
import javax.swing.Icon

class MipsColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "MIPS"
    override fun getIcon(): Icon? = MipsLanguage.icon
    override fun getHighlighter(): SyntaxHighlighter = MipsSyntaxHighlighter()
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "inst"   to AsmColors.INSTRUCTION,
        "reg"    to AsmColors.REGISTER,
        "labelD" to AsmColors.LABEL_DEF,
        "labelR" to AsmColors.LABEL_REF,
    )

    override fun getDemoText(): String = """
        .data
        msg:    .asciiz "Hello, World!\n"

        .text
        .globl __start

        <labelD>__start</labelD>:
            # Print string to stdout
            <inst>li</inst>      <reg>${'$'}v0</reg>, 4004        # sys_write
            <inst>li</inst>      <reg>${'$'}a0</reg>, 1           # stdout
            <inst>la</inst>      <reg>${'$'}a1</reg>, <labelR>msg</labelR>         # buffer
            <inst>li</inst>      <reg>${'$'}a2</reg>, 14          # length
            <inst>syscall</inst>

            # Exit program
            <inst>li</inst>      <reg>${'$'}v0</reg>, 4001        # sys_exit
            <inst>li</inst>      <reg>${'$'}a0</reg>, 0           # status
            <inst>syscall</inst>
    """.trimIndent()

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Instruction",       AsmColors.INSTRUCTION),
            AttributesDescriptor("Register",          AsmColors.REGISTER),
            AttributesDescriptor("Label definition",  AsmColors.LABEL_DEF),
            AttributesDescriptor("Label reference",   AsmColors.LABEL_REF),
            AttributesDescriptor("Directive",         AsmColors.DIRECTIVE),
            AttributesDescriptor("Identifier",        AsmColors.IDENTIFIER),
            AttributesDescriptor("Number",            AsmColors.NUMBER),
            AttributesDescriptor("String",            AsmColors.STRING),
            AttributesDescriptor("Comment",           AsmColors.COMMENT),
            AttributesDescriptor("Parentheses",       AsmColors.PARENTHESES),
            AttributesDescriptor("Comma",             AsmColors.COMMA),
            AttributesDescriptor("Operator",          AsmColors.OPERATOR),
        )
    }
}
