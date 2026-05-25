package me.lucaperri.dev.languages.editor

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import me.lucaperri.dev.languages.MipsLanguage
import me.lucaperri.dev.languages.NasmLanguage

class AsmFileTemplateGroupDescriptorFactory : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor("Assembly", NasmLanguage.icon)
        group.addTemplate(FileTemplateDescriptor("NASM Assembly File.nasm", NasmLanguage.icon))
        group.addTemplate(FileTemplateDescriptor("MIPS Assembly File.mips", MipsLanguage.icon))
        return group
    }
}
