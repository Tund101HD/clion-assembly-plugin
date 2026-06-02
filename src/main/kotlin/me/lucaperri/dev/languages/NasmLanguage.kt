package me.lucaperri.dev.languages

import com.intellij.lang.Language
import com.intellij.openapi.util.IconLoader

class NasmLanguage private constructor() : Language("NASM") {
    companion object {
        // findLanguageByID first to avoid ImplementationConflictException if another plugin
        // already claimed the "NASM" ID (e.g. com.github.nicklyra.nasm or similar).
        // plugin.xml extensions resolve by language ID string, so our features still attach
        // to whichever Language instance holds the "NASM" slot.
        @JvmField val INSTANCE: Language = Language.findLanguageByID("NASM") ?: NasmLanguage()
        val icon = IconLoader.getIcon("/icons/nasm.svg", NasmLanguage::class.java)
    }
}
