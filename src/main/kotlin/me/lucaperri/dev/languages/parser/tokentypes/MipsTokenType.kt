package me.lucaperri.dev.languages.parser.tokentypes

import com.intellij.psi.tree.IElementType
import me.lucaperri.dev.languages.MipsLanguage

class MipsTokenType(debugName: String) : IElementType(debugName, MipsLanguage.INSTANCE) {
    override fun toString(): String = "MipsTokenType.${super.toString()}"
}

class MipsElementType(debugName: String) : IElementType(debugName, MipsLanguage.INSTANCE)
