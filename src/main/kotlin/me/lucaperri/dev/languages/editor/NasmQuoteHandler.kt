package me.lucaperri.dev.languages.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.psi.tree.TokenSet
import me.lucaperri.dev.languages.psi.NasmTypes

// Enables IntelliJ's auto-insert of the closing quote when the user types
// `"` or `'` inside NASM code. Without this handler the IDE has no way to know
// which lexer token represents a string literal, so the closing quote is never
// inserted and an in-progress `"hel` lexes as BAD_CHARACTER until the user
// manually types the matching `"`.
//
// NASM accepts three quote forms (`"..."`, `'...'`, `` `...` ``) and all three
// produce a STRING token from the JFlex lexer. The platform's TypedHandler only
// fires this hook for `"` and `'`, so backtick strings still require manual
// closure — that matches the behavior in most other JetBrains languages.
class NasmQuoteHandler : SimpleTokenSetQuoteHandler(TokenSet.create(NasmTypes.STRING))
