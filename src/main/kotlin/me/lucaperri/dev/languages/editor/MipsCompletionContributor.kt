package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.highlighting.MipsInstructions
import me.lucaperri.dev.languages.highlighting.MipsRegisters
import me.lucaperri.dev.languages.psi.MipsFile
import me.lucaperri.dev.languages.psi.MipsLabelDef

class MipsCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(MipsLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val rawPrefix = currentWordPrefix(parameters)
                    val typedResult = if (rawPrefix.startsWith("$")) {
                        result.withPrefixMatcher(CamelHumpMatcher(rawPrefix))
                    } else {
                        result
                    }

                    val mnemonicCtx = isMnemonicContext(parameters, rawPrefix)
                    val file = parameters.originalFile as? MipsFile

                    if (mnemonicCtx) {
                        MipsInstructions.ALL.forEach {
                            typedResult.addElement(
                                LookupElementBuilder.create(it).withTypeText("instruction")
                            )
                        }
                    } else {
                        MipsRegisters.ALL.forEach {
                            typedResult.addElement(
                                LookupElementBuilder.create(it).withTypeText("register")
                            )
                        }
                        if (file != null) {
                            PsiTreeUtil.findChildrenOfType(file, MipsLabelDef::class.java)
                                .mapNotNull { it.name }
                                .forEach {
                                    typedResult.addElement(
                                        LookupElementBuilder.create(it).withTypeText("label")
                                    )
                                }
                        }
                    }
                }
            }
        )
    }

    private fun currentWordPrefix(parameters: CompletionParameters): String {
        val document = parameters.editor.document
        val offset = parameters.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val before = document.getText(TextRange(lineStart, offset))
        var start = before.length
        while (start > 0) {
            val c = before[start - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '$') start-- else break
        }
        return before.substring(start)
    }

    private fun isMnemonicContext(parameters: CompletionParameters, rawPrefix: String): Boolean {
        if (rawPrefix.startsWith("$")) return false
        val document = parameters.editor.document
        val offset = parameters.offset
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val before = document.getText(TextRange(lineStart, offset))
        val beforeWord = before.substring(0, before.length - rawPrefix.length).trimEnd()
        return beforeWord.isEmpty() || beforeWord.endsWith(":")
    }
}
