package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.highlighting.NasmInstructions
import me.lucaperri.dev.languages.highlighting.NasmRegisters

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
                    if (isMnemonicContext(parameters)) {
                        NasmInstructions.ALL.forEach {
                            result.addElement(LookupElementBuilder.create(it).withTypeText("instruction"))
                        }
                        listOf("section", "segment", "global", "extern", "equ", "times",
                               "db", "dw", "dd", "dq", "dt", "do",
                               "resb", "resw", "resd", "resq", "rest")
                            .forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("keyword"))
                            }
                    } else {
                        NasmRegisters.ALL.forEach {
                            result.addElement(LookupElementBuilder.create(it).withTypeText("register"))
                        }
                        listOf("byte", "word", "dword", "qword", "tword", "oword", "yword", "zword")
                            .forEach {
                                result.addElement(LookupElementBuilder.create(it).withTypeText("size"))
                            }
                        // Labels and extern names are provided by NasmLabelReference.getVariants()
                    }
                }
            }
        )
    }

    private fun isMnemonicContext(parameters: CompletionParameters): Boolean {
        val document = parameters.editor.document
        val offset = parameters.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val before = document.getText(TextRange(lineStart, offset))

        var end = before.length
        while (end > 0) {
            val c = before[end - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '.') end-- else break
        }
        val trimmed = before.substring(0, end).trimEnd()
        return trimmed.isEmpty() || trimmed.endsWith(":")
    }
}
