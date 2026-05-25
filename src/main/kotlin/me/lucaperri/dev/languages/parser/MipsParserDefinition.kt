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
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.lexer.MipsLexerAdapter
import me.lucaperri.dev.languages.psi.MipsFile
import me.lucaperri.dev.languages.psi.MipsTypes

class MipsParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType("MIPS_FILE", MipsLanguage.INSTANCE)
        val COMMENTS: TokenSet = TokenSet.create(MipsTypes.COMMENT)
        val STRINGS: TokenSet = TokenSet.create(MipsTypes.STRING)
    }

    override fun createLexer(project: Project?): Lexer = MipsLexerAdapter()

    override fun createParser(project: Project?): PsiParser = MipsParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement = MipsTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = MipsFile(viewProvider)
}
