package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.editor.MipsFoldingBuilder
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.parser.MipsParserDefinition
import me.lucaperri.dev.languages.psi.MipsFile

// Exercises MipsFoldingBuilder directly — see NasmFoldingTest for why the full
// myFixture.testFolding path isn't usable in this test environment.
class MipsFoldingTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun foldRanges(source: String): List<TextRange> {
        myFixture.configureByText("fold.mips", source)
        val file = myFixture.file as MipsFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        return MipsFoldingBuilder().buildFoldRegions(file, document, false).map { it.range }
    }

    fun testFoldNeverSwallowsSectionDirective() {
        // Before the fix, the `msg` fold extended to `main:` in .text, hiding the
        // `.text` directive line when collapsed.
        val src = ".data\nmsg: .asciiz \"hi\"\n.text\nmain:\n    li \$v0, 10\n    syscall\n"
        val ranges = foldRanges(src)
        assertTrue("expected fold regions", ranges.isNotEmpty())

        val sectionStarts = Regex("(?m)^\\.(text|data|bss|rodata)\\b")
            .findAll(src).map { it.range.first }.toList()
        for (r in ranges) {
            for (s in sectionStarts) {
                assertFalse(
                    "fold region ${r.startOffset}..${r.endOffset} swallows the section directive at $s",
                    s > r.startOffset && s < r.endOffset
                )
            }
        }
    }

    fun testSectionIsFoldable() {
        val src = ".text\nmain:\n    li \$v0, 10\n    syscall\n"
        val ranges = foldRanges(src)
        val sectionEnd = src.indexOf(".text") + ".text".length
        assertTrue(
            "expected a fold region anchored at the .text directive",
            ranges.any { it.startOffset in sectionEnd..(sectionEnd + 1) }
        )
    }

    fun testNonSectionDirectiveDoesNotBoundFold() {
        // `.globl main` is not a section change, so main's body still folds past it —
        // i.e. there is a label fold that extends beyond the `.globl` line's end.
        val src = ".text\n.globl main\nmain:\n    li \$v0, 10\n    syscall\n"
        val ranges = foldRanges(src)
        val labelColon = src.indexOf("main:") + "main:".length
        assertTrue(
            "expected main's body to fold",
            ranges.any { it.startOffset in labelColon..(labelColon + 1) && it.endOffset > labelColon + 1 }
        )
    }
}
