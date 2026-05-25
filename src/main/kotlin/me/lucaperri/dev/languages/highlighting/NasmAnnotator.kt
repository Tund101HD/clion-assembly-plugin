package me.lucaperri.dev.languages.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import me.lucaperri.dev.languages.psi.NasmIdStmt
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmTypes

// Semantic coloring: promotes IDENTIFIER tokens to register, instruction,
// label-definition, or label-reference color based on structural position.
// The "unknown mnemonic" *diagnostic* lives in NasmUnknownMnemonicInspection
// (configurable, suppressible, macro-aware) — a hard error here would
// false-positive on every user-defined %macro.
class NasmAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val node = element.node ?: return
        if (node.elementType !== NasmTypes.IDENTIFIER) return

        val parent = element.parent

        // Label definition: foo: / bar db 1 / MAX equ 100
        if (parent is NasmNamedElement && parent.nameIdentifier === element) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(AsmColors.LABEL_DEF)
                .create()
            return
        }

        val text = element.text.lowercase()

        when {
            text in NasmRegisters.ALL -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(AsmColors.REGISTER)
                    .create()
            }
            text in NasmInstructions.ALL -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(AsmColors.INSTRUCTION)
                    .create()
            }
            // Unknown mnemonic at the instruction position — leave as IDENTIFIER.
            parent is NasmIdStmt &&
            parent.node.findChildByType(NasmTypes.IDENTIFIER)?.psi === element -> { /* no-op */ }
            // Everything else (operand, extern/global symbol list, etc.) is a label reference.
            else -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(AsmColors.LABEL_REF)
                    .create()
            }
        }
    }
}
