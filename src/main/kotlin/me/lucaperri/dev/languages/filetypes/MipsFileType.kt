package me.lucaperri.dev.languages.filetypes

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import me.lucaperri.dev.languages.MipsLanguage
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

final class MipsFileType : LanguageFileType(MipsLanguage.INSTANCE) {
    companion object {
        val INSTANCE = MipsFileType()
    }
    override fun getName(): @NonNls String = "MIPS"

    override fun getDescription(): @NlsContexts.Label String = "MIPS Assembly Language"

    override fun getDefaultExtension(): @NlsSafe String = "S"

    override fun getIcon(): Icon? = MipsLanguage.icon
}