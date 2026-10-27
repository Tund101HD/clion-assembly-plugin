package me.lucaperri.dev.languages

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.NasmExternInspection
import me.lucaperri.dev.languages.parser.NasmParserDefinition
import me.lucaperri.dev.languages.psi.NasmLabelRef
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

// CAP-10: cross-file visibility of non-global labels and extern validation.
class NasmExternInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun withSettings(hide: Boolean = false, reportUndefined: Boolean = true, body: () -> Unit) {
        val s = AsmExecutableSettings.getInstance()
        val oldHide = s.hideNonGlobalLabels
        val oldReport = s.reportUndefinedExterns
        s.hideNonGlobalLabels = hide
        s.reportUndefinedExterns = reportUndefined
        try { body() } finally {
            s.hideNonGlobalLabels = oldHide
            s.reportUndefinedExterns = oldReport
        }
    }

    private fun problems(main: String): List<HighlightInfo> {
        myFixture.enableInspections(NasmExternInspection())
        myFixture.configureByText("extmain.nasm", main)
        return myFixture.doHighlighting().filter {
            val d = it.description ?: return@filter false
            "extern" in d || "global" in d || "not defined in any" in d
        }
    }

    fun testGlobalForeignLabelIsFine() = withSettings {
        myFixture.addFileToProject("lib1.nasm", "global helper\nhelper:\n    ret\n")
        assertEmpty(problems("extern helper\nmain:\n    call helper\n"))
    }

    fun testNonGlobalForeignLabelWarnsOnExternAndUse() = withSettings {
        myFixture.addFileToProject("lib2.nasm", "helper:\n    ret\n")
        val found = problems("extern helper\nmain:\n    call helper\n")
        assertEquals(2, found.size)
        assertTrue(found.all { it.severity == HighlightSeverity.WARNING })
        assertTrue(found.all { it.description!!.contains("not declared global") })
    }

    fun testNonGlobalForeignLabelIsErrorWhenHidden() = withSettings(hide = true) {
        myFixture.addFileToProject("lib3.nasm", "helper:\n    ret\n")
        val found = problems("extern helper\nmain:\n    call helper\n")
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { it.severity == HighlightSeverity.ERROR })
    }

    fun testExternOfSameFileLabelIsError() = withSettings {
        val found = problems("extern helper\nhelper:\n    ret\n")
        assertEquals(1, found.size)
        assertEquals(HighlightSeverity.ERROR, found.single().severity)
        assertTrue(found.single().description!!.contains("defined in this file"))
    }

    fun testUndefinedExternIsError() = withSettings {
        val found = problems("extern nowhere\nmain:\n    call nowhere\n")
        assertEquals(1, found.size)
        assertEquals(HighlightSeverity.ERROR, found.single().severity)
    }

    fun testLibcExternIsAllowed() = withSettings {
        assertEmpty(problems("extern printf\nmain:\n    call printf\n"))
    }

    fun testUndefinedExternCheckCanBeDisabled() = withSettings(reportUndefined = false) {
        assertEmpty(problems("extern nowhere\nmain:\n    call nowhere\n"))
    }

    fun testNonGlobalForeignLabelResolvesUnlessHidden() {
        myFixture.addFileToProject("lib4.nasm", "helper:\n    ret\n")
        myFixture.configureByText("resolve.nasm", "extern helper\nmain:\n    call helper\n")
        val ref = PsiTreeUtil.findChildrenOfType(myFixture.file, NasmLabelRef::class.java).last()
        withSettings(hide = false) { assertNotNull(ref.reference!!.resolve()) }
        withSettings(hide = true) { assertNull(ref.reference!!.resolve()) }
    }
}
