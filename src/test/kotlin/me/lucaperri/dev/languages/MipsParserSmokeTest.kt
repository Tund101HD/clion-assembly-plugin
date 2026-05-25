package me.lucaperri.dev.languages

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MipsParserSmokeTest : BasePlatformTestCase() {

    private fun assertParses(source: String) {
        myFixture.configureByText("test.mips", source)
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
            addi ${'$'}t0, ${'$'}t0, 1
            beq  ${'$'}t0, ${'$'}t1, loop
        """.trimIndent()
    )

    fun testInlineLabelAndInstruction() = assertParses("loop: addi ${'$'}t0, ${'$'}t0, 1\n")

    fun testTextSection() = assertParses(
        """
        .text
        .globl main

        main:
            li ${'$'}v0, 10
            syscall
        """.trimIndent()
    )

    fun testDataSection() = assertParses(
        """
        .data
        msg: .asciiz "Hello, world!"
        nums: .word 1, 2, 3, 4
        zero: .byte 0
        """.trimIndent()
    )

    fun testMemoryOperand() = assertParses(
        """
        lw ${'$'}t0, 0(${'$'}sp)
        sw ${'$'}ra, 4(${'$'}sp)
        lw ${'$'}fp, -8(${'$'}sp)
        """.trimIndent()
    )

    fun testRegisterNumbers() = assertParses(
        """
        add ${'$'}1, ${'$'}2, ${'$'}3
        add ${'$'}t0, ${'$'}zero, ${'$'}ra
        """.trimIndent()
    )

    fun testFileWithoutTrailingNewline() = assertParses("li ${'$'}v0, 10")

    fun testEmptyFile() = assertParses("")

    fun testCommentOnlyFile() = assertParses("# just a header comment")

    fun testFloatingPointInstructions() = assertParses(
        """
        add.s ${'$'}f0, ${'$'}f1, ${'$'}f2
        mtc1  ${'$'}t0, ${'$'}f0
        """.trimIndent()
    )
}
