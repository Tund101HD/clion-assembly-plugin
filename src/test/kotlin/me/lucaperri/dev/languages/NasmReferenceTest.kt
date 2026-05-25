package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition
import me.lucaperri.dev.languages.psi.NasmNamedElement

// Now that the contributor patterns on NasmLabelRef (a composite ASTWrapperPsiElement)
// rather than the raw IDENTIFIER leaf, myFixture.file.findReferenceAt works correctly
// in tests: ASTWrapperPsiElement.getReferences() calls PsiReferenceService which
// consults the contributor. The previous workaround (direct registry call on the leaf)
// is no longer needed.
class NasmReferenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun referenceAtCaret(): PsiReference? =
        myFixture.file.findReferenceAt(myFixture.caretOffset)

    fun testResolveSameFile() {
        myFixture.configureByText(
            "main.nasm",
            """
            target:
                ret
            caller:
                call tar<caret>get
            """.trimIndent()
        )
        val ref = referenceAtCaret() ?: error("no reference at caret")
        val resolved = ref.resolve() as? NasmNamedElement
            ?: error("did not resolve to a NasmNamedElement")
        assertEquals("target", resolved.name)
    }

    fun testResolveCrossFile() {
        myFixture.configureByText(
            "helpers.nasm",
            """
            global do_thing
            do_thing:
                ret
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.nasm",
            """
            extern do_thing
            main:
                call do_t<caret>hing
            """.trimIndent()
        )
        val ref = referenceAtCaret() ?: error("no reference at caret")
        val resolved = ref.resolve() as? NasmNamedElement
            ?: error("did not resolve to a NasmNamedElement")
        assertEquals("do_thing", resolved.name)
    }

    fun testMnemonicIsNotReference() {
        myFixture.configureByText(
            "main.nasm",
            """
            main:
                mo<caret>v rax, 1
            """.trimIndent()
        )
        val ref = referenceAtCaret()
        assertNull("mnemonic should not produce a reference", ref)
    }

    fun testLabelDefinitionIsNotReference() {
        myFixture.configureByText(
            "main.nasm",
            """
            ta<caret>rget:
                ret
            """.trimIndent()
        )
        val ref = referenceAtCaret()
        assertNull("label definition should not produce a reference", ref)
    }

    fun testUnresolvedReference() {
        myFixture.configureByText(
            "main.nasm",
            """
            main:
                call no_such_la<caret>bel
            """.trimIndent()
        )
        val ref = referenceAtCaret() ?: error("no reference at caret")
        assertNull("should not resolve a label that doesn't exist", ref.resolve())
    }

    fun testExternResolvesToCrossFileLabel() {
        myFixture.configureByText(
            "helpers.nasm",
            """
            global do_thing
            do_thing:
                ret
            """.trimIndent()
        )
        myFixture.configureByText(
            "main.nasm",
            """
            extern do_t<caret>hing
            main:
                call do_thing
            """.trimIndent()
        )
        val ref = referenceAtCaret() ?: error("no reference at caret on extern identifier")
        val resolved = ref.resolve() as? NasmNamedElement
            ?: error("extern identifier did not resolve to a NasmNamedElement")
        assertEquals("do_thing", resolved.name)
    }
}
