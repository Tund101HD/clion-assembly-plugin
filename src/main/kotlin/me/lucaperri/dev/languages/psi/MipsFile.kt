package me.lucaperri.dev.languages.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.filetypes.MipsFileType

class MipsFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, MipsLanguage.INSTANCE) {

    override fun getFileType(): FileType = MipsFileType.INSTANCE

    override fun toString(): String = "MIPS"
}
