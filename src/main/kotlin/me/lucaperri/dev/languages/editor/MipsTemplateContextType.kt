package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import me.lucaperri.dev.languages.MipsLanguage

class MipsTemplateContextType : TemplateContextType("MIPS") {
    override fun isInContext(context: TemplateActionContext): Boolean =
        context.file.language === MipsLanguage.INSTANCE
}
