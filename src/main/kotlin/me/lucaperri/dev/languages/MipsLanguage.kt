package me.lucaperri.dev.languages

import com.intellij.lang.Language
import com.intellij.openapi.util.IconLoader

class MipsLanguage private constructor() : Language("MIPS") {
    companion object {
        @JvmField val INSTANCE = MipsLanguage()
        val icon = IconLoader.getIcon("/icons/mips.svg", MipsLanguage::class.java)
    }
}
