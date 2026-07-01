package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.editor.NasmFoldingBuilder
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition
import me.lucaperri.dev.languages.psi.NasmFile

// The FoldingBuilder is exercised directly (not via myFixture.testFolding), which
// would require the full plugin.xml registration that BasePlatformTestCase can't
// load in the CLion-dependent test environment.
class NasmFoldingTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun foldRanges(source: String): List<TextRange> {
        myFixture.configureByText("fold.nasm", source)
        val file = myFixture.file as NasmFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        return NasmFoldingBuilder().buildFoldRegions(file, document, false).map { it.range }
    }

    fun testFoldNeverSwallowsSectionHeader() {
        // Before the fix, the `msg` fold extended to `main:` in the next section,
        // hiding the `section .text` header when collapsed.
        val src = "section .data\nmsg db \"hi\"\nsection .text\nmain:\n    mov eax, 1\n    ret\n"
        val ranges = foldRanges(src)
        assertTrue("expected fold regions", ranges.isNotEmpty())

        val sectionStarts = Regex("(?m)^section\\b").findAll(src).map { it.range.first }.toList()
        for (r in ranges) {
            for (s in sectionStarts) {
                assertFalse(
                    "fold region ${r.startOffset}..${r.endOffset} swallows the section header at $s",
                    s > r.startOffset && s < r.endOffset
                )
            }
        }
    }

    fun testSectionIsFoldable() {
        // The `.text` section body (main's code) should be foldable as its own region,
        // anchored on the section line.
        val src = "section .text\nmain:\n    mov eax, 1\n    ret\n"
        val ranges = foldRanges(src)
        val sectionKwEnd = src.indexOf("section .text") + "section .text".length
        assertTrue(
            "expected a fold region anchored at the section header",
            ranges.any { it.startOffset in sectionKwEnd..(sectionKwEnd + 1) }
        )
    }

    fun testLabelBodyFoldsWithinSection() {
        val src = "section .text\nmain:\n    mov eax, 1\n    ret\n"
        val ranges = foldRanges(src)
        val labelColon = src.indexOf("main:") + "main:".length
        assertTrue(
            "expected a fold region for main's body",
            ranges.any { it.startOffset in labelColon..(labelColon + 1) && it.endOffset > labelColon + 1 }
        )
    }
}
