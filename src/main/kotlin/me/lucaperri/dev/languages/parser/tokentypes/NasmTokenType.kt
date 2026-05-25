package me.lucaperri.dev.languages.parser.tokentypes

import com.intellij.psi.tree.IElementType
import me.lucaperri.dev.languages.NasmLanguage

class NasmTokenType(debugName: String) : IElementType(debugName, NasmLanguage.INSTANCE) {
    override fun toString(): String = "NasmTokenType.${super.toString()}"
}

class NasmElementType(debugName: String) : IElementType(debugName, NasmLanguage.INSTANCE)
