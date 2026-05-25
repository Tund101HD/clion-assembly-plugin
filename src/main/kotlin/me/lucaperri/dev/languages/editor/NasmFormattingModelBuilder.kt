package me.lucaperri.dev.languages.editor

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val spacingBuilder = SpacingBuilder(settings, NasmLanguage.INSTANCE)
            .before(NasmTypes.COMMA).spacing(0, 0, 0, false, 0)
            .after(NasmTypes.COMMA).spacing(1, 1, 0, false, 0)
            .around(NasmTypes.PLUS).spacing(1, 1, 0, false, 0)
            .around(NasmTypes.MINUS).spacing(1, 1, 0, false, 0)
            .around(NasmTypes.STAR).spacing(1, 1, 0, false, 0)
            .after(NasmTypes.LBRACKET).spacing(0, 0, 0, false, 0)
            .before(NasmTypes.RBRACKET).spacing(0, 0, 0, false, 0)
            .before(NasmTypes.COLON).spacing(0, 0, 0, false, 0)

        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            NasmBlock(formattingContext.node, spacingBuilder),
            settings
        )
    }
}

private class NasmBlock(
    node: ASTNode,
    private val spacingBuilder: SpacingBuilder
) : AbstractBlock(node, /* wrap = */ null, /* alignment = */ null) {

    override fun buildChildren(): List<Block> {
        val children = mutableListOf<Block>()
        var child = myNode.firstChildNode
        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE && child.textRange.length > 0) {
                children += NasmBlock(child, spacingBuilder)
            }
            child = child.treeNext
        }
        return children
    }

    override fun getIndent(): Indent? = Indent.getNoneIndent()

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacingBuilder.getSpacing(this, child1, child2)

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes =
        ChildAttributes(Indent.getNoneIndent(), null)
}
