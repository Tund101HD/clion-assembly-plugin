package me.lucaperri.dev.languages.filetypes

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import me.lucaperri.dev.languages.NasmLanguage
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

final class NasmFileType : LanguageFileType(NasmLanguage.INSTANCE) {
    companion object{
        val INSTANCE = NasmFileType()
    }

    override fun getName(): @NonNls String = "NASM"

    override fun getDescription(): @NlsContexts.Label String = "NASM Assembly Language"

    override fun getDefaultExtension(): @NlsSafe String = "asm"

    override fun getIcon(): Icon? = NasmLanguage.icon
}