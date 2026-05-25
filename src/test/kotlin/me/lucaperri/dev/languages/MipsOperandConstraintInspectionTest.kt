package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.inspections.MipsOperandConstraintInspection
import me.lucaperri.dev.languages.parser.MipsParserDefinition

class MipsOperandConstraintInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun issues(source: String) = run {
        myFixture.enableInspections(MipsOperandConstraintInspection())
        myFixture.configureByText("constraint.mips", source)
        myFixture.doHighlighting().filter {
            it.description?.contains("expects") == true ||
            it.description?.contains("must be") == true
        }
    }

    private fun hasIssue(source: String) = issues(source).isNotEmpty()

    // --- Correct forms ---

    fun testAdduCorrectNotFlagged() =
        assertFalse(hasIssue("main:\n    addu \$a0, \$t0, \$t1\n"))

    fun testLwCorrectNotFlagged() =
        assertFalse(hasIssue("main:\n    lw \$t0, 4(\$sp)\n"))

    fun testBeqCorrectNotFlagged() =
        assertFalse(hasIssue("main:\n    beq \$t0, \$t1, main\n"))

    fun testSyscallNoOperandsNotFlagged() =
        assertFalse(hasIssue("main:\n    syscall\n"))

    fun testJalrOneRegNotFlagged() =
        assertFalse(hasIssue("main:\n    jalr \$t0\n"))

    fun testJalrTwoRegsNotFlagged() =
        assertFalse(hasIssue("main:\n    jalr \$ra, \$t0\n"))

    fun testAddiuCorrectNotFlagged() =
        assertFalse(hasIssue("main:\n    addiu \$t0, \$t1, 4\n"))

    // --- Wrong operand type ---

    fun testAdduMemOperandFlagged() =
        assertTrue(hasIssue("main:\n    addu \$a0, 12(\$t0), \$t1\n"))

    fun testAdduTwoMemOperandsFlagged() =
        assertTrue(hasIssue("main:\n    addu \$a0, 12(\$t0), 16(\$t0)\n"))

    fun testLwRegInsteadOfMemFlagged() =
        assertTrue(hasIssue("main:\n    lw \$t0, \$sp\n"))

    fun testBeqImmInsteadOfLabelFlagged() =
        assertTrue(hasIssue("main:\n    beq \$t0, \$t1, 4\n"))

    // --- Wrong arity ---

    fun testAddTooFewFlagged() =
        assertTrue(hasIssue("main:\n    add \$t0\n"))

    fun testAdduTooManyFlagged() =
        assertTrue(hasIssue("main:\n    addu \$a0, \$t0, \$t1, \$t2\n"))

    fun testSyscallWithOperandFlagged() =
        assertTrue(hasIssue("main:\n    syscall \$v0\n"))
}
