package me.lucaperri.dev.languages.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.psi.MipsIdStmt
import me.lucaperri.dev.languages.psi.MipsLocalLabelRef
import me.lucaperri.dev.languages.psi.MipsNamedElement
import me.lucaperri.dev.languages.psi.MipsTypes

// Semantic coloring: promotes IDENTIFIER tokens to register, instruction,
// label-definition, or label-reference color based on structural position.
// See NasmAnnotator for rationale on unknown-mnemonic handling.
class MipsAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val node = element.node ?: return
        val parent = element.parent

        // Local labels (.done:, .loop_top) are DIRECTIVE tokens in the lexer.
        // Color them as label-def or label-ref based on structural position.
        if (node.elementType === MipsTypes.DIRECTIVE) {
            if (parent is MipsNamedElement && parent.nameIdentifier === element) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element).textAttributes(AsmColors.LABEL_DEF).create()
            } else if (parent is MipsLocalLabelRef) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element).textAttributes(AsmColors.LABEL_REF).create()
            }
            return
        }

        if (node.elementType !== MipsTypes.IDENTIFIER) return

        // Label definition: foo:
        if (parent is MipsNamedElement && parent.nameIdentifier === element) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(AsmColors.LABEL_DEF)
                .create()
            return
        }

        val text = element.text.lowercase()

        when {
            text in MipsRegisters.ALL -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(AsmColors.REGISTER)
                    .create()
            }
            text in MipsInstructions.ALL -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(AsmColors.INSTRUCTION)
                    .create()
            }
            // Unknown mnemonic at the instruction position — leave as IDENTIFIER.
            parent is MipsIdStmt &&
            parent.node.findChildByType(MipsTypes.IDENTIFIER)?.psi === element -> { /* no-op */ }
            // Everything else (operand, .globl/.extern symbol, etc.) is a label reference.
            else -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(AsmColors.LABEL_REF)
                    .create()
            }
        }
    }
}
