package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.NasmDuplicateLabelInspection
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class NasmDuplicateLabelInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun duplicateCount(source: String): Int {
        myFixture.enableInspections(NasmDuplicateLabelInspection())
        myFixture.configureByText("dup.nasm", source)
        return myFixture.doHighlighting().count {
            it.description?.contains("Duplicate label") == true
        }
    }

    fun testSameNameGlobalLabelsFlagged() {
        // Regression: two identical non-local labels are still duplicates.
        assertTrue(duplicateCount("foo:\n    ret\nfoo:\n    ret\n") >= 1)
    }

    fun testLocalLabelsInDifferentScopesNotFlagged() {
        // `.loop` under func1 and `.loop` under func2 are distinct symbols in NASM.
        val src = """
            func1:
            .loop:
                jmp .loop
            func2:
            .loop:
                jmp .loop
        """.trimIndent()
        assertEquals(0, duplicateCount(src))
    }

    fun testLocalLabelDuplicatedWithinOneScopeFlagged() {
        val src = """
            func1:
            .loop:
                nop
            .loop:
                nop
        """.trimIndent()
        assertTrue(duplicateCount(src) >= 1)
    }

    fun testLocalAndGlobalWithSameTailNotFlagged() {
        // `.foo` (local) and `foo` (global) are different names; neither collides.
        val src = """
            foo:
                ret
            bar:
            .foo:
                jmp .foo
        """.trimIndent()
        assertEquals(0, duplicateCount(src))
    }
}
