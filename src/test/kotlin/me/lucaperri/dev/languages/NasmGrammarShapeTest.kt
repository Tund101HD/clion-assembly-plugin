package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition

// Asserts the *shape* of the parsed PSI tree, not just the absence of error
// elements. A malformed-but-error-free parse (e.g. `section .text` where `.text`
// is parsed as a separate statement rather than the section's operand) silently
// breaks structure view, folding, and the external-annotator error gate.
class NasmGrammarShapeTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun tree(source: String): String {
        myFixture.configureByText("test.nasm", source)
        return DebugUtil.psiToString(myFixture.file, true, false).trim()
    }

    fun testSectionDirectiveShape() {
        val tree = tree("section .text")
        // `.text` must be the operand INSIDE section_stmt, not a sibling statement.
        assertTrue(
            "expected SECTION_STMT to wrap the operand; tree was:\n$tree",
            tree.contains("NasmSectionStmtImpl(SECTION_STMT)")
        )
        val sectionIdx = tree.indexOf("SECTION_STMT")
        val operandIdx = tree.indexOf("OPERAND", sectionIdx)
        val identIdx = tree.indexOf("'.text'")
        assertTrue("'.text' should appear in the tree:\n$tree", identIdx >= 0)
        assertTrue(
            "OPERAND containing '.text' should be nested under SECTION_STMT; tree was:\n$tree",
            sectionIdx in 0 until operandIdx && operandIdx in 0 until identIdx
        )
    }

    fun testRelMemoryOperandShape() {
        val tree = tree("lea rdi, [rel msg]")
        // `rel` must be consumed as the mem-prefix and `msg` as the address
        // expression, both nested under a single MEMORY_OPERAND.
        assertTrue(
            "expected MEMORY_OPERAND; tree was:\n$tree",
            tree.contains("NasmMemoryOperandImpl(MEMORY_OPERAND)")
        )
        val memIdx = tree.indexOf("MEMORY_OPERAND")
        val relIdx = tree.indexOf("'rel'", memIdx)
        val msgIdx = tree.indexOf("'msg'", memIdx)
        assertTrue("'rel' should be inside MEMORY_OPERAND:\n$tree", relIdx in (memIdx + 1) until msgIdx)
        assertTrue("'msg' should be inside MEMORY_OPERAND:\n$tree", msgIdx > relIdx)
    }

    fun testPlainMemoryOperandHasNoPrefix() {
        // `[rax]` must parse `rax` as the expression, NOT as a dangling mem-prefix.
        val tree = tree("mov rax, [rbx]")
        val memIdx = tree.indexOf("MEMORY_OPERAND")
        assertTrue("expected MEMORY_OPERAND; tree was:\n$tree", memIdx >= 0)
        val exprIdx = tree.indexOf("EXPRESSION", memIdx)
        val rbxIdx = tree.indexOf("'rbx'", memIdx)
        assertTrue(
            "'rbx' should be the EXPRESSION, not a prefix; tree was:\n$tree",
            exprIdx in (memIdx + 1) until rbxIdx
        )
    }

    fun testColonEquShape() {
        val tree = tree("colon_len: equ 14")
        assertTrue(
            "expected LABEL_DEF for 'colon_len'; tree was:\n$tree",
            tree.contains("NasmLabelDefImpl(LABEL_DEF)")
        )
        assertTrue(
            "expected EQU_STMT for the `equ 14` tail; tree was:\n$tree",
            tree.contains("NasmEquStmtImpl(EQU_STMT)")
        )
    }
}
