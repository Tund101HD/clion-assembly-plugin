package me.lucaperri.dev.languages

import com.intellij.lang.Language
import com.intellij.openapi.util.IconLoader

class MipsLanguage private constructor() : Language("MIPS") {
    companion object {
        // findLanguageByID first to avoid ImplementationConflictException if another plugin
        // already claimed the "MIPS" ID.
        @JvmField val INSTANCE: Language = Language.findLanguageByID("MIPS") ?: MipsLanguage()
        val icon = IconLoader.getIcon("/icons/mips.svg", MipsLanguage::class.java)
    }
}
