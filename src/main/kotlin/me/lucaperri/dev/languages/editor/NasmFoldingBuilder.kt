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
import me.lucaperri.dev.languages.psi.NasmSectionStmt

class NasmFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> {
        if (root !is NasmFile) return FoldingDescriptor.EMPTY_ARRAY

        val fileEnd = root.textRange.endOffset
        val descriptors = mutableListOf<FoldingDescriptor>()

        // Private grammar rules are flattened, so section statements and label defs
        // are both direct children of the file, already in document order.
        val sections = root.children.filterIsInstance<NasmSectionStmt>()
        val labels = root.children.filter { it is NasmNamedElement }

        // Each `section ...` folds its whole body — up to the next section, or EOF.
        // Anchoring on the section node keeps the header line visible when collapsed.
        for (i in sections.indices) {
            val section = sections[i]
            val start = section.textRange.endOffset
            val rawEnd = sections.getOrNull(i + 1)?.textRange?.startOffset ?: fileEnd
            addRegion(descriptors, document, section.node, start, rawEnd)
        }

        // Each label folds its body — up to the next label OR the next section,
        // whichever comes first, so a label's fold never swallows a section header
        // (the bug where collapsing the last label of a section hid `section .text`).
        for (i in labels.indices) {
            val label = labels[i]
            val start = label.textRange.endOffset
            val nextLabel = labels.getOrNull(i + 1)?.textRange?.startOffset ?: fileEnd
            val nextSection = sections
                .firstOrNull { it.textRange.startOffset >= start }
                ?.textRange?.startOffset ?: fileEnd
            addRegion(descriptors, document, label.node, start, minOf(nextLabel, nextSection))
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = " ..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private fun addRegion(
        out: MutableList<FoldingDescriptor>,
        document: Document,
        node: ASTNode,
        start: Int,
        rawEnd: Int
    ) {
        val end = trimTrailingWhitespace(document, start, rawEnd)
        if (end - start < 2) return
        out += FoldingDescriptor(node, TextRange(start, end))
    }

    private fun trimTrailingWhitespace(document: Document, start: Int, end: Int): Int {
        var e = end
        val text = document.charsSequence
        while (e > start && text[e - 1].isWhitespace()) e--
        return e
    }
}
