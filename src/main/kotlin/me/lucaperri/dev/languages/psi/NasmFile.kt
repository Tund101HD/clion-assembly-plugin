package me.lucaperri.dev.languages.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import me.lucaperri.dev.languages.NasmLanguage
import me.lucaperri.dev.languages.filetypes.NasmFileType

class NasmFile(viewProvider: FileViewProvider) :
    PsiFileBase(viewProvider, NasmLanguage.INSTANCE) {

    override fun getFileType(): FileType = NasmFileType.INSTANCE

    override fun toString(): String = "NASM"
}
