package me.lucaperri.dev.languages

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.lucaperri.dev.languages.filetypes.NasmFileType
import me.lucaperri.dev.languages.inspections.NasmOperandTypeInspection
import me.lucaperri.dev.languages.parser.NasmParserDefinition

class NasmOperandTypeInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().runWriteAction {
            FileTypeManager.getInstance().associateExtension(NasmFileType.INSTANCE, "nasm")
        }
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(
            NasmLanguage.INSTANCE, NasmParserDefinition(), testRootDisposable
        )
    }

    private fun hasTypeError(source: String): Boolean {
        myFixture.enableInspections(NasmOperandTypeInspection())
        myFixture.configureByText("test.nasm", source)
        return myFixture.doHighlighting().any { it.description?.contains("requires a") == true }
    }

    // ── SSE XMM-only ──────────────────────────────────────────────────────────

    fun testXorpsXmmNotFlagged() =
        assertFalse(hasTypeError("main:\n    xorps xmm0, xmm1\n"))

    fun testXorpsMemoryNotFlagged() =
        assertFalse(hasTypeError("main:\n    xorps xmm0, [rax]\n"))

    fun testXorpsGprFlagged() =
        assertTrue(hasTypeError("main:\n    xorps ax, xmm1\n"))

    fun testXorpsSegFlagged() =
        assertTrue(hasTypeError("main:\n    xorps xmm0, cs\n"))

    // The exact case reported by the user
    fun testXorpsGprAndSegBothFlagged() =
        assertTrue(hasTypeError("main:\n    xorps ax, cs\n"))

    fun testAddpsXmmNotFlagged() =
        assertFalse(hasTypeError("main:\n    addps xmm1, xmm2\n"))

    fun testAddpsYmmFlagged() =
        assertTrue(hasTypeError("main:\n    addps ymm0, ymm1\n"))

    // ── SSE2 dual-form integer (XMM or MMX both valid) ────────────────────────

    fun testPxorXmmNotFlagged() =
        assertFalse(hasTypeError("main:\n    pxor xmm0, xmm1\n"))

    fun testPxorMmxNotFlagged() =
        assertFalse(hasTypeError("main:\n    pxor mm0, mm1\n"))

    fun testPxorGprFlagged() =
        assertTrue(hasTypeError("main:\n    pxor rax, rbx\n"))

    fun testPxorSegFlagged() =
        assertTrue(hasTypeError("main:\n    pxor xmm0, cs\n"))

    // ── AVX VEX (XMM or YMM) ─────────────────────────────────────────────────

    fun testVxorpsXmmNotFlagged() =
        assertFalse(hasTypeError("main:\n    vxorps xmm0, xmm1, xmm2\n"))

    fun testVxorpsYmmNotFlagged() =
        assertFalse(hasTypeError("main:\n    vxorps ymm0, ymm1, ymm2\n"))

    fun testVxorpsGprFlagged() =
        assertTrue(hasTypeError("main:\n    vxorps rax, ymm1, ymm2\n"))

    fun testVxorpsZmmFlagged() =
        assertTrue(hasTypeError("main:\n    vxorps zmm0, zmm1, zmm2\n"))

    // ── x87 ST-only ──────────────────────────────────────────────────────────

    fun testFxchStNotFlagged() =
        assertFalse(hasTypeError("main:\n    fxch st1\n"))

    fun testFxchGprFlagged() =
        assertTrue(hasTypeError("main:\n    fxch rax\n"))

    fun testFxchXmmFlagged() =
        assertTrue(hasTypeError("main:\n    fxch xmm0\n"))

    // x87 with memory operand — must not false-positive
    fun testFaddMemoryNotFlagged() =
        assertFalse(hasTypeError("main:\n    fadd dword [rax]\n"))

    // ── Non-SIMD instructions — no false positives ────────────────────────────

    fun testMovGprNotFlagged() =
        assertFalse(hasTypeError("main:\n    mov rax, rbx\n"))

    fun testMovdMixedNotFlagged() =
        assertFalse(hasTypeError("main:\n    movd xmm0, eax\n"))

    fun testCvtsi2ssNotFlagged() =
        assertFalse(hasTypeError("main:\n    cvtsi2ss xmm0, rax\n"))
}
