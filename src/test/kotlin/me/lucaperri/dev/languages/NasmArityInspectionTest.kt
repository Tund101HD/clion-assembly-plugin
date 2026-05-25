package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.NasmArityInspection
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class NasmArityInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun hasArityIssue(source: String): Boolean {
        myFixture.enableInspections(NasmArityInspection())
        myFixture.configureByText("arity.nasm", source)
        return myFixture.doHighlighting().any { it.description?.contains("operand") == true }
    }

    // --- Correct arities ---

    fun testMovTwoOperandsNotFlagged() =
        assertFalse(hasArityIssue("main:\n    mov rax, rbx\n"))

    fun testPushOneOperandNotFlagged() =
        assertFalse(hasArityIssue("main:\n    push rax\n"))

    fun testNopNoOperandsNotFlagged() =
        assertFalse(hasArityIssue("main:\n    nop\n"))

    fun testRetNoOperandsNotFlagged() =
        assertFalse(hasArityIssue("main:\n    ret\n"))

    fun testImulTwoOperandsNotFlagged() =
        assertFalse(hasArityIssue("main:\n    imul rax, rbx\n"))

    fun testImulThreeOperandsNotFlagged() =
        assertFalse(hasArityIssue("main:\n    imul rax, rbx, 5\n"))

    fun testShldThreeOperandsNotFlagged() =
        assertFalse(hasArityIssue("main:\n    shld rax, rbx, cl\n"))

    // Prefix mnemonics: `rep movsb` parses as id_stmt "rep" with 1 operand,
    // but rep is not in the arity table, so it must not be flagged.
    fun testRepPrefixNotFlagged() =
        assertFalse(hasArityIssue("main:\n    rep movsb\n"))

    // --- Wrong arities ---

    fun testMovOneOperandFlagged() =
        assertTrue(hasArityIssue("main:\n    mov rax\n"))

    fun testMovThreeOperandsFlagged() =
        assertTrue(hasArityIssue("main:\n    mov rax, rbx, rcx\n"))

    fun testAddOneOperandFlagged() =
        assertTrue(hasArityIssue("main:\n    add rax\n"))

    fun testNopWithOperandFlagged() =
        assertTrue(hasArityIssue("main:\n    nop rax\n"))

    fun testPushNoOperandsFlagged() =
        assertTrue(hasArityIssue("main:\n    push\n"))

    fun testImulOneOperandFlagged() =
        assertTrue(hasArityIssue("main:\n    imul rax\n"))
}
