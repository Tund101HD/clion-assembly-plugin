package me.lucaperri.dev.languages.lexer

import com.intellij.lexer.FlexAdapter

class MipsLexerAdapter : FlexAdapter(MipsLexer(null))
