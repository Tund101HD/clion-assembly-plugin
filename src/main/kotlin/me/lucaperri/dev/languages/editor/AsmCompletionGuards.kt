package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

// Places where neither the NASM nor the MIPS completion contributor should offer
// anything: integer literals (no identifier can start with a digit, so the popup
// only ever showed noise like `r10` while typing `10`) and — unless enabled in
// Settings → Assembly → General — comments.
object AsmCompletionGuards {

    fun shouldSuppress(position: PsiElement, document: Document, offset: Int): Boolean =
        isInSuppressedComment(position) || isTypingNumber(position, document, offset)

    private fun isInSuppressedComment(position: PsiElement): Boolean =
        !AsmExecutableSettings.getInstance().completeInComments &&
            PsiTreeUtil.getParentOfType(position, PsiComment::class.java, false) != null

    private fun isTypingNumber(position: PsiElement, document: Document, offset: Int): Boolean {
        val nasm = position.language.isKindOf(NasmLanguage.INSTANCE)
        val text = document.charsSequence
        var start = offset
        while (start > 0 && isWordChar(text[start - 1], nasm)) start--
        if (start == offset) return false
        val first = text[start]
        if (first.isDigit()) return true
        // NASM also accepts `$`-prefixed hex (`$1F`); in MIPS `$1` is a register.
        return nasm && first == '$' && start + 1 < offset && text[start + 1].isDigit()
    }

    private fun isWordChar(c: Char, nasm: Boolean): Boolean =
        c.isLetterOrDigit() || c == '_' || c == '$' || (nasm && (c == '.' || c == '?'))
}

// Keeps the completion popup from opening by itself in the same places; an
// explicit Ctrl+Space still runs the contributors, which then add nothing.
class AsmCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(
        editor: Editor,
        contextElement: PsiElement,
        psiFile: PsiFile,
        offset: Int
    ): ThreeState =
        if (AsmCompletionGuards.shouldSuppress(contextElement, editor.document, offset)) ThreeState.YES
        else ThreeState.UNSURE
}
