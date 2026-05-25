package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.MipsUnknownMnemonicInspection
import me.lucaperri.dev.languages.inspections.NasmUnknownMnemonicInspection
import me.lucaperri.dev.languages.parser.MipsParserDefinition
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class UnknownMnemonicInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun nasmWarnsUnknown(source: String): Boolean {
        myFixture.enableInspections(NasmUnknownMnemonicInspection())
        myFixture.configureByText("t.nasm", source)
        return myFixture.doHighlighting().any {
            it.description?.contains("Unknown mnemonic") == true
        }
    }

    private fun mipsWarnsUnknown(source: String): Boolean {
        myFixture.enableInspections(MipsUnknownMnemonicInspection())
        myFixture.configureByText("t.mips", source)
        return myFixture.doHighlighting().any {
            it.description?.contains("Unknown mnemonic") == true
        }
    }

    fun testNasmUnknownIsFlagged() =
        assertTrue(nasmWarnsUnknown("main:\n    notarealinstr rax, 1\n"))

    fun testNasmKnownIsNotFlagged() =
        assertFalse(nasmWarnsUnknown("main:\n    mov rax, 1\n    syscall\n"))

    fun testNasmMacroIsNotFlagged() =
        assertFalse(
            nasmWarnsUnknown(
                """
                %macro prologue 0
                    push rbp
                %endmacro
                main:
                    prologue
                """.trimIndent()
            )
        )

    fun testNasmDefineIsNotFlagged() =
        assertFalse(
            nasmWarnsUnknown(
                """
                %define spin nop
                main:
                    spin
                """.trimIndent()
            )
        )

    fun testMipsUnknownIsFlagged() =
        assertTrue(mipsWarnsUnknown("main:\n    notreal ${'$'}t0, ${'$'}t1\n"))

    fun testMipsKnownIsNotFlagged() =
        assertFalse(mipsWarnsUnknown("main:\n    addi ${'$'}t0, ${'$'}zero, 1\n    syscall\n"))

    fun testMipsPseudoIsNotFlagged() =
        assertFalse(mipsWarnsUnknown("main:\n    li ${'$'}v0, 10\n    move ${'$'}t0, ${'$'}t1\n"))
}
