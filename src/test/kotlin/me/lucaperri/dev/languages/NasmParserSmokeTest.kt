package me.lucaperri.dev.languages

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NasmParserSmokeTest : BasePlatformTestCase() {

    private fun assertParses(source: String) {
        myFixture.configureByText("test.nasm", source)
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) {
            fail(
                "Expected clean parse but got ${errors.size} error(s):\n" +
                    errors.joinToString("\n") { "  - '${it.errorDescription}' at ${it.textRange}: ${it.text}" } +
                    "\n\nSource:\n$source"
            )
        }
    }

    fun testBareLabel() = assertParses("foo:\n")

    fun testLabelThenIndentedInstruction() = assertParses(
        """
        loop:
            mov rbp, rsp
            push rbp
        """.trimIndent()
    )

    fun testInlineLabelAndInstruction() = assertParses("loop: mov rbp, rsp\n")

    fun testSectionDirective() = assertParses(
        """
        section .text
        global _start
        _start:
            mov rax, 60
            syscall
        """.trimIndent()
    )

    fun testExternGlobal() = assertParses(
        """
        extern puts, malloc
        global main
        main:
            ret
        """.trimIndent()
    )

    fun testEquConstant() = assertParses("SYS_EXIT equ 60\n")

    fun testDataDirectives() = assertParses(
        """
        section .data
        msg: db "Hello, world!", 10
        len equ 14
        colon_len: equ 14
        nums: dw 1, 2, 3
        qwords: dq 0xDEADBEEF
        """.trimIndent()
    )

    fun testMemoryOperand() = assertParses(
        """
        mov rax, [rbp + 16]
        mov rbx, [rsp - 8]
        lea rsi, [msg]
        lea rdi, [rel msg]
        mov rax, [abs 0x401000]
        """.trimIndent()
    )

    fun testTimesDirective() = assertParses("zeros: times 64 db 0\n")

    fun testPreprocessorDirective() = assertParses(
        """
        %include "common.inc"
        %define MAX 256
        %assign counter counter+1
        %define BUFSIZE 8*1024
        """.trimIndent()
    )

    fun testCommentsAndStrings() = assertParses(
        """
        ; This is a comment
        section .data         ; section directive
        msg: db "Hello, world!", 0
        empty: db "", 0
        """.trimIndent()
    )

    fun testFileWithoutTrailingNewline() = assertParses("mov rax, 1")

    fun testEmptyFile() = assertParses("")

    fun testCommentOnlyFile() = assertParses("; just a header comment")
}
