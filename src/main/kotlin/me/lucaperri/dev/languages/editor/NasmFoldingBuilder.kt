package me.lucaperri.dev.languages.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmNamedElement

class NasmFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> {
        if (root !is NasmFile) return FoldingDescriptor.EMPTY_ARRAY

        val labels = collectLabels(root)
        if (labels.isEmpty()) return FoldingDescriptor.EMPTY_ARRAY

        val fileEnd = root.textRange.endOffset
        val descriptors = mutableListOf<FoldingDescriptor>()

        for (i in labels.indices) {
            val label = labels[i]
            val next = labels.getOrNull(i + 1)
            val start = label.textRange.endOffset
            val rawEnd = next?.textRange?.startOffset ?: fileEnd
            val end = trimTrailingWhitespace(document, start, rawEnd)
            if (end - start < 2) continue
            descriptors += FoldingDescriptor(label.node, TextRange(start, end))
        }
        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = " ..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private fun trimTrailingWhitespace(document: Document, start: Int, end: Int): Int {
        var e = end
        val text = document.charsSequence
        while (e > start && text[e - 1].isWhitespace()) e--
        return e
    }

    private fun collectLabels(file: NasmFile): List<PsiElement> =
        file.children.filter { it is NasmNamedElement }
}
