package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.psi.tree.TokenSet
import me.lucaperri.dev.languages.psi.MipsTypes

// Auto-inserts the closing `"` when typing a string literal in MIPS code.
// See NasmQuoteHandler for rationale — MIPS only has one string form.
class MipsQuoteHandler : SimpleTokenSetQuoteHandler(TokenSet.create(MipsTypes.STRING))
