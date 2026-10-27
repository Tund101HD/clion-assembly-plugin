package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.editor.AsmCompletionGuards
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.MipsParserDefinition
import me.lucaperri.dev.languages.parser.NasmParserDefinition
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

// CAP-8 (no completion while typing integer literals) and CAP-9 (no completion
// inside comments unless enabled in settings).
class AsmCompletionGuardsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun suppressed(fileName: String, text: String): Boolean {
        myFixture.configureByText(fileName, text)
        val offset = myFixture.caretOffset
        val element = myFixture.file.findElementAt(offset - 1)!!
        return AsmCompletionGuards.shouldSuppress(element, myFixture.editor.document, offset)
    }

    private fun withCommentCompletion(enabled: Boolean, body: () -> Unit) {
        val settings = AsmExecutableSettings.getInstance()
        val old = settings.completeInComments
        settings.completeInComments = enabled
        try { body() } finally { settings.completeInComments = old }
    }

    fun testNasmDecimalSuppressed() = assertTrue(suppressed("n.nasm", "mov rax, 10<caret>\n"))

    fun testNasmHexSuffixSuppressed() = assertTrue(suppressed("n.nasm", "mov rax, 1fh<caret>\n"))

    fun testNasmDollarHexSuppressed() = assertTrue(suppressed("n.nasm", "mov rax, \$1F<caret>\n"))

    fun testNasmRegisterNotSuppressed() = assertFalse(suppressed("n.nasm", "mov r1<caret>\n"))

    fun testMipsNumberSuppressed() = assertTrue(suppressed("m.mips", "li \$t0, 42<caret>\n"))

    fun testMipsNumericRegisterNotSuppressed() = assertFalse(suppressed("m.mips", "move \$1<caret>\n"))

    fun testNasmCommentSuppressedByDefault() = withCommentCompletion(false) {
        assertTrue(suppressed("n.nasm", "mov rax, 1 ; load the cou<caret>\n"))
    }

    fun testMipsCommentSuppressedByDefault() = withCommentCompletion(false) {
        assertTrue(suppressed("m.mips", "nop # some wor<caret>\n"))
    }

    fun testCommentAllowedWhenEnabled() = withCommentCompletion(true) {
        assertFalse(suppressed("n.nasm", "mov rax, 1 ; load the cou<caret>\n"))
    }
}
