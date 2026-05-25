package me.lucaperri.dev.languages

import com.intellij.lang.Language
import com.intellij.openapi.util.IconLoader

class NasmLanguage private constructor() : Language("NASM") {
    companion object {
        @JvmField val INSTANCE = NasmLanguage()
        val icon = IconLoader.getIcon("/icons/nasm.svg", NasmLanguage::class.java)
    }
}
