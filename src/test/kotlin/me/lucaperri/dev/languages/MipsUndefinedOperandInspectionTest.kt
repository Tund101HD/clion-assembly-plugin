package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.filetypes.MipsFileType
import me.lucaperri.dev.languages.inspections.MipsUndefinedOperandInspection
import me.lucaperri.dev.languages.parser.MipsParserDefinition

class MipsUndefinedOperandInspectionTest : BasePlatformTestCase() {

    // The plugin depends on com.intellij.modules.clion, which fails to register in the
    // test environment because CLion's own JAR cannot resolve intellij.cidr.apinotes.xml.
    // Register only the two extension points the inspection tests actually need.
    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(MipsFileType.INSTANCE, "mips")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            MipsLanguage.INSTANCE, MipsParserDefinition(), testRootDisposable
        )
    }

    private fun highlights(source: String) = run {
        myFixture.enableInspections(MipsUndefinedOperandInspection())
        myFixture.configureByText("undef.mips", source)
        myFixture.doHighlighting()
    }

    fun testDefinedLabelNotFlagged() =
        assertFalse(highlights("main:\n    j main\n").any {
            it.description?.contains("Undefined label") == true
        })

    fun testUndefinedLabelIsFlagged() =
        assertTrue(highlights("main:\n    j nowhere\n").any {
            it.description?.contains("Undefined label") == true &&
            it.description?.contains("nowhere") == true
        })

    fun testKnownRegisterNotFlagged() =
        assertFalse(highlights("main:\n    add ${'$'}t0, ${'$'}t1, ${'$'}t2\n").any {
            it.description?.contains("Unknown register") == true
        })

    fun testUnknownRegisterIsFlagged() =
        assertTrue(highlights("main:\n    add ${'$'}x99, ${'$'}t1, ${'$'}t2\n").any {
            it.description?.contains("Unknown register") == true
        })

    fun testMemoryWithKnownRegisterNotFlagged() =
        assertFalse(highlights("main:\n    lw ${'$'}t0, 4(${'$'}t1)\n").any {
            it.description?.contains("Expected a register") == true ||
            it.description?.contains("Unknown register") == true
        })

    fun testMemoryWithNonRegisterIsFlagged() =
        assertTrue(highlights("main:\n    lw ${'$'}t0, 4(foo)\n").any {
            it.description?.contains("Expected a register") == true
        })

    fun testLocalLabelNotFlagged() =
        assertFalse(highlights("main:\n.done:\n    beq ${'$'}t0, ${'$'}t1, .done\n").any {
            it.description?.contains("Undefined") == true
        })
}
