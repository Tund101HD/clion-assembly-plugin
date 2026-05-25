package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.NasmUndefinedSymbolInspection
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class NasmUndefinedSymbolInspectionTest : BasePlatformTestCase() {

    // The plugin depends on com.intellij.modules.clion, which fails to register in the
    // test environment because CLion's own JAR cannot resolve intellij.cidr.apinotes.xml.
    // Register only the two extension points the inspection tests actually need.
    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun hasUndefined(source: String): Boolean {
        myFixture.enableInspections(NasmUndefinedSymbolInspection())
        // "undef.nasm" is unique to this class — avoids LightVirtualFile name-cache
        // collisions with UnknownMnemonicInspectionTest which also creates "t.nasm".
        myFixture.configureByText("undef.nasm", source)
        return myFixture.doHighlighting().any {
            it.description?.contains("Undefined symbol") == true
        }
    }

    fun testDefinedLabelNotFlagged() =
        assertFalse(hasUndefined("target:\n    ret\nmain:\n    call target\n"))

    fun testUndefinedLabelIsFlagged() =
        assertTrue(hasUndefined("main:\n    call nowhere\n"))

    fun testExternDeclaredNotFlagged() =
        assertFalse(hasUndefined("extern ext_fn\nmain:\n    call ext_fn\n"))

    fun testGlobalListNotFlagged() =
        assertFalse(hasUndefined("global main\nmain:\n    ret\n"))

    fun testDefineConstantNotFlagged() =
        assertFalse(hasUndefined("%define MY_CONST 42\nmain:\n    mov rax, MY_CONST\n"))

    fun testRegisterNotFlagged() =
        assertFalse(hasUndefined("main:\n    mov rax, rbx\n"))

    fun testRepPrefixNotFlagged() =
        assertFalse(hasUndefined("main:\n    rep movsb\n"))

    fun testDeclareAsExternFix_insertsAtTop() {
        myFixture.configureByText("undef.nasm", "main:\n    call ext_fn\n")
        myFixture.enableInspections(NasmUndefinedSymbolInspection())
        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().find { "ext_fn" in it.text }
        assertNotNull("DeclareAsExternFix not offered", fix)
        myFixture.launchAction(fix!!)
        assertEquals("extern ext_fn\nmain:\n    call ext_fn\n", myFixture.editor.document.text)
    }

    fun testDeclareAsExternFix_appendsAfterLastExtern() {
        myFixture.configureByText("undef.nasm", "extern foo\nmain:\n    call ext_fn\n")
        myFixture.enableInspections(NasmUndefinedSymbolInspection())
        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().find { "ext_fn" in it.text }
        assertNotNull("DeclareAsExternFix not offered", fix)
        myFixture.launchAction(fix!!)
        assertEquals("extern foo\nextern ext_fn\nmain:\n    call ext_fn\n", myFixture.editor.document.text)
    }

    fun testDeclareAsExternFix_notOfferedOutsideCall() {
        myFixture.configureByText("undef.nasm", "main:\n    mov rax, nowhere\n")
        myFixture.enableInspections(NasmUndefinedSymbolInspection())
        myFixture.doHighlighting()
        val fix = myFixture.getAllQuickFixes().find { "Declare" in it.text }
        assertNull("DeclareAsExternFix should not be offered for non-call operands", fix)
    }
}
