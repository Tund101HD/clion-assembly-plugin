package me.lucaperri.dev.languages.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import me.lucaperri.dev.languages.NasmLanguage
import javax.swing.Icon

class NasmColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "NASM"
    override fun getIcon(): Icon? = NasmLanguage.icon
    override fun getHighlighter(): SyntaxHighlighter = NasmSyntaxHighlighter()
    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "inst"   to AsmColors.INSTRUCTION,
        "reg"    to AsmColors.REGISTER,
        "labelD" to AsmColors.LABEL_DEF,
        "labelR" to AsmColors.LABEL_REF,
    )

    override fun getDemoText(): String = """
        section .data
            msg:    db "Hello, World!", 10
            msglen: equ ${'$'}-msg

        section .text
            global _start

        <labelD>_start</labelD>:
            ; Print string to stdout
            <inst>mov</inst>     <reg>rax</reg>, 1           ; sys_write
            <inst>mov</inst>     <reg>rdi</reg>, 1           ; stdout
            <inst>mov</inst>     <reg>rsi</reg>, <labelR>msg</labelR>         ; buffer
            <inst>mov</inst>     <reg>rdx</reg>, <labelR>msglen</labelR>      ; length
            <inst>syscall</inst>

            ; Exit program
            <inst>mov</inst>     <reg>rax</reg>, 60          ; sys_exit
            <inst>xor</inst>     <reg>rdi</reg>, <reg>rdi</reg>         ; status 0
    """.trimIndent()

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Instruction",       AsmColors.INSTRUCTION),
            AttributesDescriptor("Register",          AsmColors.REGISTER),
            AttributesDescriptor("Label definition",  AsmColors.LABEL_DEF),
            AttributesDescriptor("Label reference",   AsmColors.LABEL_REF),
            AttributesDescriptor("Directive",         AsmColors.DIRECTIVE),
            AttributesDescriptor("Keyword",           AsmColors.KEYWORD),
            AttributesDescriptor("Size specifier",    AsmColors.SIZE_SPEC),
            AttributesDescriptor("Identifier",        AsmColors.IDENTIFIER),
            AttributesDescriptor("Number",            AsmColors.NUMBER),
            AttributesDescriptor("String",            AsmColors.STRING),
            AttributesDescriptor("Comment",           AsmColors.COMMENT),
            AttributesDescriptor("Brackets",          AsmColors.BRACKETS),
            AttributesDescriptor("Comma",             AsmColors.COMMA),
            AttributesDescriptor("Operator",          AsmColors.OPERATOR),
        )
    }
}
