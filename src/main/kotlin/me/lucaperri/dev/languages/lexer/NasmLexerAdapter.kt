package me.lucaperri.dev.languages.lexer

import com.intellij.lexer.FlexAdapter

class NasmLexerAdapter : FlexAdapter(NasmLexer(null))
