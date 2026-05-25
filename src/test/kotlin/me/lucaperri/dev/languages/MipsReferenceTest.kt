package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.parser.MipsParserDefinition
import me.lucaperri.dev.languages.psi.MipsLabelDef

// See NasmReferenceTest for the rationale: contributor now patterns on MipsLabelRef
// (composite) so findReferenceAt works in tests as it does in the real IDE.
class MipsReferenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun referenceAtCaret(): PsiReference? =
        myFixture.file.findReferenceAt(myFixture.caretOffset)

    fun testResolveSameFile() {
        myFixture.configureByText(
            "main.mips",
            """
            target:
                jr ${'$'}ra
            caller:
                jal tar<caret>get
            """.trimIndent()
        )
        val ref = referenceAtCaret() ?: error("no reference at caret")
        val resolved = ref.resolve() as? MipsLabelDef
            ?: error("did not resolve to a MipsLabelDef")
        assertEquals("target", resolved.name)
    }

    fun testResolveCrossFile() {
        myFixture.configureByText(
            "helpers.mips",
            """
            .globl do_thing
            do_thing:
                jr ${'$'}ra
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.mips",
            """
            main:
                jal do_t<caret>hing
            """.trimIndent()
        )
        val ref = referenceAtCaret() ?: error("no reference at caret")
        val resolved = ref.resolve() as? MipsLabelDef
            ?: error("did not resolve to a MipsLabelDef")
        assertEquals("do_thing", resolved.name)
    }

    fun testMnemonicIsNotReference() {
        myFixture.configureByText(
            "main.mips",
            """
            main:
                ad<caret>di ${'$'}t0, ${'$'}zero, 1
            """.trimIndent()
        )
        val ref = referenceAtCaret()
        assertNull("mnemonic should not produce a reference", ref)
    }

    fun testLabelDefinitionIsNotReference() {
        myFixture.configureByText(
            "main.mips",
            """
            ta<caret>rget:
                jr ${'$'}ra
            """.trimIndent()
        )
        val ref = referenceAtCaret()
        assertNull("label definition should not produce a reference", ref)
    }

    fun testRegisterIsNotReference() {
        myFixture.configureByText(
            "main.mips",
            """
            main:
                addi ${'$'}t<caret>0, ${'$'}zero, 1
            """.trimIndent()
        )
        val ref = referenceAtCaret()
        assertNull("register operand should not produce a reference", ref)
    }
}
