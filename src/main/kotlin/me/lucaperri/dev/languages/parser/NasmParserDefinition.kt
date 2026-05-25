package me.lucaperri.dev.languages.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.lexer.NasmLexerAdapter
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.psi.NasmTypes

class NasmParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType("NASM_FILE", NasmLanguage.INSTANCE)
        val COMMENTS: TokenSet = TokenSet.create(NasmTypes.COMMENT)
        val STRINGS: TokenSet = TokenSet.create(NasmTypes.STRING)
    }

    override fun createLexer(project: Project?): Lexer = NasmLexerAdapter()

    override fun createParser(project: Project?): PsiParser = NasmParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement = NasmTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NasmFile(viewProvider)
}
