package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.inspections.MipsAlignmentInspection
import me.lucaperri.dev.languages.parser.MipsParserDefinition

class MipsAlignmentInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun hasAlignment(source: String): Boolean {
        myFixture.enableInspections(MipsAlignmentInspection())
        myFixture.configureByText("align.mips", source)
        return myFixture.doHighlighting().any { it.description?.contains("aligned") == true }
    }

    // --- lw / sw (4-byte alignment) ---

    fun testLwAlignedNotFlagged() =
        assertFalse(hasAlignment("main:\n    lw \$t0, 4(\$sp)\n"))

    fun testLwZeroNotFlagged() =
        assertFalse(hasAlignment("main:\n    lw \$t0, 0(\$sp)\n"))

    fun testLwUnalignedFlagged() =
        assertTrue(hasAlignment("main:\n    lw \$t0, 5(\$sp)\n"))

    fun testSwUnalignedFlagged() =
        assertTrue(hasAlignment("main:\n    sw \$t0, 6(\$sp)\n"))

    fun testSwAlignedNotFlagged() =
        assertFalse(hasAlignment("main:\n    sw \$t0, 8(\$sp)\n"))

    fun testNegativeAlignedNotFlagged() =
        assertFalse(hasAlignment("main:\n    lw \$t0, -4(\$sp)\n"))

    fun testNegativeUnalignedFlagged() =
        assertTrue(hasAlignment("main:\n    lw \$t0, -5(\$sp)\n"))

    // --- lh / sh (2-byte alignment) ---

    fun testLhAlignedNotFlagged() =
        assertFalse(hasAlignment("main:\n    lh \$t0, 2(\$sp)\n"))

    fun testLhUnalignedFlagged() =
        assertTrue(hasAlignment("main:\n    lh \$t0, 3(\$sp)\n"))

    fun testShAlignedNotFlagged() =
        assertFalse(hasAlignment("main:\n    sh \$t0, 6(\$sp)\n"))

    // --- lb / sb (byte — no alignment required) ---

    fun testLbAnyOffsetNotFlagged() =
        assertFalse(hasAlignment("main:\n    lb \$t0, 5(\$sp)\n"))

    fun testSbAnyOffsetNotFlagged() =
        assertFalse(hasAlignment("main:\n    sb \$t0, 7(\$sp)\n"))

    // --- No offset (bare memory operand) — no false positive ---

    fun testLwNoOffsetNotFlagged() =
        assertFalse(hasAlignment("main:\n    lw \$t0, 0(\$sp)\n"))

    // --- Hex offset ---

    fun testLwHexAlignedNotFlagged() =
        assertFalse(hasAlignment("main:\n    lw \$t0, 0x10(\$sp)\n"))

    fun testLwHexUnalignedFlagged() =
        assertTrue(hasAlignment("main:\n    lw \$t0, 0x11(\$sp)\n"))
}
