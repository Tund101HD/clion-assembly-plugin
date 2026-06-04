package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.docs.NasmDocumentationProvider
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition
import me.lucaperri.dev.languages.psi.NasmNamedElement

class NasmLabelDocTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun docFor(source: String): String {
        myFixture.configureByText("test.nasm", source)
        val label = PsiTreeUtil.findChildOfType(myFixture.file, NasmNamedElement::class.java)
            ?: error("No NasmNamedElement parsed from:\n$source")
        return NasmDocumentationProvider().generateDoc(label, null)
            ?: error("generateDoc returned null for label '${label.name}'")
    }

    // The original parser only recognised Input|Output|Flags|Clobbers, so any
    // following `Result:` / `Operands:` / etc. lines got swallowed as continuation
    // text under whichever section happened to be open. Verify that each known
    // popup-section header now opens its own section.
    fun testResultGetsOwnSectionNotMergedWithOperands() {
        val doc = docFor(
            """
            ; Sums two integers.
            ; Operands: rdi, rsi
            ; Result:   rax = rdi + rsi
            add_pair:
                lea rax, [rdi + rsi]
                ret
            """.trimIndent()
        )

        val resultIdx = doc.indexOf(">Result:<")
        val operandsIdx = doc.indexOf(">Operands:<")
        assertTrue("Result: should appear as its own section header; got:\n$doc", resultIdx >= 0)
        assertTrue("Operands: should appear as its own section header; got:\n$doc", operandsIdx >= 0)
        val operandsBlock = doc.substring(operandsIdx, resultIdx)
        assertFalse(
            "Result body was misattributed to the Operands section; saw:\n$operandsBlock",
            operandsBlock.contains("rax = rdi + rsi"),
        )
    }

    fun testInputAliasesToOperandsHeader() {
        // Legacy comments use Input:/Output:; the parser now renders them under
        // the canonical Operands:/Result: labels so the popup matches the IDE's
        // own instruction-doc taxonomy.
        val doc = docFor(
            """
            ; Sums two integers.
            ; Input:  rdi, rsi
            ; Output: rax = rdi + rsi
            add_pair:
                ret
            """.trimIndent()
        )
        assertTrue("Operands: header should appear (aliased from Input:); got:\n$doc",
            doc.contains(">Operands:<"))
        assertTrue("Result: header should appear (aliased from Output:); got:\n$doc",
            doc.contains(">Result:<"))
        assertFalse("Raw Input: header should NOT appear once aliased; got:\n$doc",
            doc.contains(">Input:<"))
        assertFalse("Raw Output: header should NOT appear once aliased; got:\n$doc",
            doc.contains(">Output:<"))
    }

    fun testCustomSectionHeaderRecognised() {
        // User-defined section labels should also become their own sections —
        // the regex is permissive on purpose so users can document
        // project-specific concerns (TODO:, Side-effects:, etc.).
        val doc = docFor(
            """
            ; Updates the global counter.
            ; Note: not thread-safe
            ; Todo: add CAS variant
            bump_counter:
                ret
            """.trimIndent()
        )
        assertTrue("Note: should appear in doc; got:\n$doc", doc.contains(">Note:<"))
        assertTrue("Todo: should appear in doc; got:\n$doc", doc.contains(">Todo:<"))
    }

    fun testKnownSectionsOrderedByCanonicalList() {
        // The user can author sections in any order; the popup should still
        // surface them in the canonical Flags/Operands/Clobbers/.../Note order.
        val doc = docFor(
            """
            ; Bit-twiddle helper.
            ; Note:     unsafe in interrupt context
            ; Clobbers: rax
            ; Flags:    ZF, CF
            twiddle:
                ret
            """.trimIndent()
        )
        val flagsIdx = doc.indexOf(">Flags:<")
        val clobbersIdx = doc.indexOf(">Clobbers:<")
        val noteIdx = doc.indexOf(">Note:<")
        assertTrue("Flags: header missing; got:\n$doc", flagsIdx >= 0)
        assertTrue("Clobbers: header missing; got:\n$doc", clobbersIdx >= 0)
        assertTrue("Note: header missing; got:\n$doc", noteIdx >= 0)
        assertTrue(
            "Sections should appear in Flags -> Clobbers -> Note order; offsets " +
                "were Flags=$flagsIdx Clobbers=$clobbersIdx Note=$noteIdx",
            flagsIdx < clobbersIdx && clobbersIdx < noteIdx,
        )
    }
}
