package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.highlighting.NasmInstructions
import me.lucaperri.dev.languages.highlighting.NasmRegisters
import me.lucaperri.dev.languages.psi.NasmExternStmt
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmNamedElement
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(NasmLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val ctx = lineContext(parameters)
                    val file = parameters.originalFile as? NasmFile

                    when (ctx) {
                        LineContext.MNEMONIC -> {
                            NasmInstructions.ALL.forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("instruction"))
                            }
                            STMT_KEYWORDS.forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("keyword"))
                            }
                            // db/dw/.../resb/resq/... — also legal at the start of a labeled-data
                            // statement, but more commonly appear on a fresh line as well.
                            DATA_KEYWORDS.forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("keyword"))
                            }
                        }
                        LineContext.AFTER_BARE_IDENT -> {
                            // `name <caret>` — could be `name db ...`, `name equ ...`, or `name resb ...`.
                            // Mnemonics are deliberately suppressed: NASM doesn't allow `name mov ...`
                            // and offering instructions here just clutters the popup.
                            DATA_KEYWORDS.forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("data"))
                            }
                            result.addElement(LookupElementBuilder.create("equ").withTypeText("keyword"))
                            result.addElement(LookupElementBuilder.create("times").withTypeText("keyword"))
                        }
                        LineContext.OPERAND -> {
                            NasmRegisters.ALL.forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("register"))
                            }
                            SIZE_SPECS.forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("size"))
                            }
                            // NasmLabelReference.getVariants() supplies these when the parser has
                            // produced a NasmLabelRef at the caret — but on partially-parsed lines
                            // (typing in the middle of editing) the wrapper may not exist yet.
                            // Adding them here covers the gap; CompletionResultSet dedups by
                            // lookup string so we never double-list.
                            if (file != null) addProjectLabels(file, result)
                        }
                    }
                }
            }
        )
    }

    private fun addProjectLabels(file: NasmFile, result: CompletionResultSet) {
        PsiTreeUtil.findChildrenOfType(file, NasmNamedElement::class.java)
            .mapNotNull { it.name }
            .forEach {
                result.addElement(LookupElementBuilder.create(it).withTypeText("label"))
            }
        PsiTreeUtil.findChildrenOfType(file, NasmExternStmt::class.java)
            .flatMap { it.labelRefList }
            .mapNotNull { it.node.findChildByType(NasmTypes.IDENTIFIER)?.text }
            .forEach {
                result.addElement(LookupElementBuilder.create(it).withTypeText("extern"))
            }
    }

    private enum class LineContext { MNEMONIC, AFTER_BARE_IDENT, OPERAND }

    private fun lineContext(parameters: CompletionParameters): LineContext {
        val document = parameters.editor.document
        val offset = parameters.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val before = document.getText(TextRange(lineStart, offset))

        // Strip the prefix word the user is currently typing, keep everything before it.
        var end = before.length
        while (end > 0) {
            val c = before[end - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '.') end-- else break
        }
        // trim() (not trimEnd()) so leading indentation is dropped too — otherwise an
        // indented `    buffer <caret>` keeps its leading spaces, fails BARE_IDENTIFIER_LINE
        // (which is anchored with ^[a-zA-Z_.?]), and never reaches AFTER_BARE_IDENT, so
        // db/resb/resq/... are never offered on indented data lines.
        val trimmed = before.substring(0, end).trim()

        if (trimmed.isEmpty() || trimmed.endsWith(":")) return LineContext.MNEMONIC

        // `<ident> <caret>` — exactly one bare identifier preceded by optional indent,
        // no punctuation. This could be either:
        //   - data-statement label position: `msg db "..."`, `buffer resb 64`
        //   - operand position after a mnemonic: `mov eax, 1`
        // Disambiguate on the identifier itself — if it's a known mnemonic, the cursor
        // is at the operand of an id_stmt; otherwise it's almost certainly a label name
        // about to be followed by db/equ/resb/etc.
        if (trimmed.matches(BARE_IDENTIFIER_LINE)) {
            return if (trimmed.lowercase() in NasmInstructions.ALL) {
                LineContext.OPERAND
            } else {
                LineContext.AFTER_BARE_IDENT
            }
        }

        return LineContext.OPERAND
    }

    companion object {
        private val BARE_IDENTIFIER_LINE = Regex("""^[a-zA-Z_.?][a-zA-Z0-9_.?$]*$""")

        private val STMT_KEYWORDS = listOf("section", "segment", "global", "extern", "equ", "times")
        private val DATA_KEYWORDS = listOf(
            "db", "dw", "dd", "dq", "dt", "do",
            "resb", "resw", "resd", "resq", "rest"
        )
        private val SIZE_SPECS = listOf("byte", "word", "dword", "qword", "tword", "oword", "yword", "zword")
    }
}
