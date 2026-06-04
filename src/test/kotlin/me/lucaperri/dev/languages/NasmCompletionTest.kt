package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition

// Operand-position completions exercise NasmLabelReference.getVariants(), which is
// reachable from the PSI mixin and therefore available under BasePlatformTestCase
// without loading plugin.xml. Tests that require NasmCompletionContributor to fire
// directly (start-of-line mnemonic suggestions, the data-keyword position after a
// bare label identifier) are exercised through the IDE — BasePlatformTestCase does
// not register the contributor and there's no public test-only API to do so.
class NasmCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    fun testExternNameOfferedInCompletion() {
        myFixture.configureByText("main.nasm", "extern printf\nmain:\n    call <caret>\n")
        val items = myFixture.completeBasic()
        assertNotNull("completion items should not be null", items)
        assertTrue("extern name 'printf' should appear in completion",
            items.any { it.lookupString == "printf" })
    }

    fun testSameFileLabelOfferedInCompletion() {
        myFixture.configureByText("main.nasm", "helper:\n    ret\nmain:\n    call <caret>\n")
        val items = myFixture.completeBasic()
        assertNotNull(items)
        assertTrue("same-file label 'helper' should appear in completion",
            items.any { it.lookupString == "helper" })
    }

    fun testCrossFileLabelOfferedInCompletion() {
        myFixture.addFileToProject("lib.nasm", "lib_fn:\n    ret\n")
        myFixture.configureByText("main.nasm", "main:\n    call <caret>\n")
        val items = myFixture.completeBasic()
        assertNotNull(items)
        assertTrue("cross-file label 'lib_fn' should appear in completion",
            items.any { it.lookupString == "lib_fn" })
    }

    fun testDataLabelStringOfferedAsOperand() {
        // `msg db "..."` defines `msg` via NasmDataLabelDef, not NasmLabelDef.
        // The original getVariants() only looked at NasmLabelDef, so this would fail.
        val src = """
            section .data
            msg db "Hello, world!", 10
            section .text
            main:
                mov rsi, <caret>
        """.trimIndent()
        myFixture.configureByText("main.nasm", src)
        val items = myFixture.completeBasic()
        assertNotNull(items)
        assertTrue("data label 'msg' should appear in operand completion",
            items.any { it.lookupString == "msg" })
    }

    fun testEquLengthSymbolOfferedAsOperand() {
        // `msg_len equ $ - msg` defines `msg_len` via NasmEquLabelDef.
        val src = """
            section .data
            msg db "Hi", 0
            msg_len equ $ - msg
            section .text
            main:
                mov rdx, <caret>
        """.trimIndent()
        myFixture.configureByText("main.nasm", src)
        val items = myFixture.completeBasic()
        assertNotNull(items)
        assertTrue("equ symbol 'msg_len' should appear in operand completion",
            items.any { it.lookupString == "msg_len" })
    }
}
