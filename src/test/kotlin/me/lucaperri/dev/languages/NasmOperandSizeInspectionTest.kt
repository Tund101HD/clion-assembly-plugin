package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.NasmOperandSizeInspection
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class NasmOperandSizeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun hasSizeIssue(source: String): Boolean {
        myFixture.enableInspections(NasmOperandSizeInspection())
        myFixture.configureByText("size.nasm", source)
        return myFixture.doHighlighting().any {
            it.description?.contains("mismatch", ignoreCase = true) == true ||
            it.description?.contains("wider", ignoreCase = true) == true
        }
    }

    // --- Register–register ---

    fun testMovRegRegSameSizeNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    mov rax, rbx\n"))

    fun testMovRegRegMismatchFlagged() =
        assertTrue(hasSizeIssue("main:\n    mov al, rbx\n"))

    fun testAddRegRegMismatchFlagged() =
        assertTrue(hasSizeIssue("main:\n    add eax, rbx\n"))

    fun testCmpRegRegSameSizeNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    cmp rax, rcx\n"))

    // --- SIZE_SPEC vs register ---

    fun testSizeSpecMatchNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    mov byte [rsp], al\n"))

    fun testSizeSpecMismatchFlagged() =
        assertTrue(hasSizeIssue("main:\n    mov byte [rsp], rax\n"))

    fun testQwordSizeSpecMatchNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    mov qword [rsp], rax\n"))

    // --- movzx / movsx (destination must be wider) ---

    fun testMovzxCorrectNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    movzx rax, bl\n"))

    fun testMovzxDestTooSmallFlagged() =
        assertTrue(hasSizeIssue("main:\n    movzx bl, rax\n"))

    fun testMovsxCorrectNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    movsx eax, bl\n"))

    fun testMovsxDestSameSizeFlagged() =
        assertTrue(hasSizeIssue("main:\n    movsx al, bl\n"))

    // --- Shifts: count operand (cl) must not trigger mismatch ---

    fun testShlWithClNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    shl rax, cl\n"))

    fun testShrWithClNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    shr eax, cl\n"))

    // --- Immediate / unknown-size operands: no false positive ---

    fun testImmediateNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    mov rax, 5\n"))

    fun testUnknownMemoryNotFlagged() =
        assertFalse(hasSizeIssue("main:\n    mov rax, [rbp]\n"))
}
