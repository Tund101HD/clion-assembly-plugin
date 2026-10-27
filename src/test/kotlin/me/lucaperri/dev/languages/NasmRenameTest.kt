package me.lucaperri.dev.languages

import com.intellij.lang.LanguageNamesValidation
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.parser.NasmParserDefinition
import me.lucaperri.dev.languages.psi.NasmLabelDef
import me.lucaperri.dev.languages.references.NasmNamesValidator
import me.lucaperri.dev.languages.references.NasmRenameInputValidator

// CAP-7: renaming a local label failed with "Inserted identifier is not valid"
// because the platform fell back to Java identifier rules, which reject the leading dot.
class NasmRenameTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
        // plugin.xml isn't loaded under BasePlatformTestCase (see NasmUndefinedSymbolInspectionTest).
        LanguageNamesValidation.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmNamesValidator(), testRootDisposable
        )
        RenameInputValidator.EP_NAME.point.registerExtension(NasmRenameInputValidator(), testRootDisposable)
    }

    private val source = """
        func:
        .loop:
            dec rcx
            jnz .loop
            ret
    """.trimIndent()

    private fun label(name: String): NasmLabelDef {
        myFixture.configureByText("rename.nasm", source)
        return PsiTreeUtil.findChildrenOfType(myFixture.file, NasmLabelDef::class.java)
            .first { it.name == name }
    }

    private fun isValid(name: String, newName: String): Boolean =
        RenameUtil.isValidName(project, label(name), newName)

    fun testLocalLabelAcceptsLocalName() = assertTrue(isValid(".loop", ".again"))

    fun testLocalLabelRejectsGlobalName() = assertFalse(isValid(".loop", "again"))

    fun testGlobalLabelRejectsLocalName() = assertFalse(isValid("func", ".func"))

    fun testGlobalLabelAcceptsNasmIdentifierChars() = assertTrue(isValid("func", "my_func?2"))

    fun testRegisterNameRejected() = assertFalse(isValid("func", "rax"))

    fun testRenameLocalLabelUpdatesReferences() {
        myFixture.configureByText("rename.nasm", source.replace(".loop:", ".lo<caret>op:"))
        myFixture.renameElementAtCaret(".again")
        myFixture.checkResult(source.replace(".loop", ".again"))
    }
}
