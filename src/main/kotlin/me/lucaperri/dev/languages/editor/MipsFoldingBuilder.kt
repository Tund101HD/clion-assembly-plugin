package me.lucaperri.dev.languages.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.psi.MipsDirectiveStmt
import me.lucaperri.dev.languages.psi.MipsFile
import me.lucaperri.dev.languages.psi.MipsLabelDef
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean
    ): Array<FoldingDescriptor> {
        if (root !is MipsFile) return FoldingDescriptor.EMPTY_ARRAY

        val fileEnd = root.textRange.endOffset
        val descriptors = mutableListOf<FoldingDescriptor>()

        // MIPS has no `section` keyword — section changes are dot-directives
        // (`.text`, `.data`, `.bss`, ...). Only the section-defining ones bound folds;
        // `.globl` / `.align` / `.word` and friends are ordinary body statements.
        val sections = root.children
            .filterIsInstance<MipsDirectiveStmt>()
            .filter { it.isSectionDirective() }
        val labels = root.children.filterIsInstance<MipsLabelDef>()

        // Each section directive folds its whole body — up to the next section, or EOF.
        for (i in sections.indices) {
            val section = sections[i]
            val start = section.textRange.endOffset
            val rawEnd = sections.getOrNull(i + 1)?.textRange?.startOffset ?: fileEnd
            addRegion(descriptors, document, section.node, start, rawEnd)
        }

        // Each label folds its body — up to the next label OR the next section
        // directive, whichever comes first, so a fold never swallows a `.data`/`.text`
        // header line.
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

    private fun MipsDirectiveStmt.isSectionDirective(): Boolean {
        val name = node.findChildByType(MipsTypes.DIRECTIVE)?.text?.lowercase() ?: return false
        return name in SECTION_DIRECTIVES
    }

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

    private companion object {
        // GNU as / MARS / SPIM section-defining directives. `.section <name>` carries
        // its target as an operand; matching on `.section` itself is enough to bound a fold.
        private val SECTION_DIRECTIVES = setOf(
            ".text", ".data", ".bss", ".rodata", ".rdata",
            ".sdata", ".sbss", ".kdata", ".ktext",
            ".lit4", ".lit8", ".section"
        )
    }
}
