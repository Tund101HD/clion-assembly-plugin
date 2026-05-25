package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import me.lucaperri.dev.languages.NasmLanguage

class NasmTemplateContextType : TemplateContextType("NASM") {
    override fun isInContext(context: TemplateActionContext): Boolean =
        context.file.language === NasmLanguage.INSTANCE
}
