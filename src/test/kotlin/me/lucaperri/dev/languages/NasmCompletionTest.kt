package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class NasmCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    fun testExternNameOfferedInCompletion() {
        myFixture.configureByText("main.nasm", "extern printf\nmain:\n    call <caret>\n")
        val items = myFixture.completeBasic()
        assertNotNull("completion items should not be null", items)
        assertTrue("extern name 'printf' should appear in completion",
            items.any { it.lookupString == "printf" })
    }

    fun testSameFileLabelOfferedInCompletion() {
        myFixture.configureByText("main.nasm", "helper:\n    ret\nmain:\n    call <caret>\n")
        val items = myFixture.completeBasic()
        assertNotNull(items)
        assertTrue("same-file label 'helper' should appear in completion",
            items.any { it.lookupString == "helper" })
    }

    fun testCrossFileLabelOfferedInCompletion() {
        myFixture.addFileToProject("lib.nasm", "lib_fn:\n    ret\n")
        myFixture.configureByText("main.nasm", "main:\n    call <caret>\n")
        val items = myFixture.completeBasic()
        assertNotNull(items)
        assertTrue("cross-file label 'lib_fn' should appear in completion",
            items.any { it.lookupString == "lib_fn" })
    }
}
