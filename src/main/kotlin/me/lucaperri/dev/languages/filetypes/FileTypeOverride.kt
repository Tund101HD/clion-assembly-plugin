package me.lucaperri.dev.languages.filetypes

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

class FileTypeOverride : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        val ext = file.extension?.lowercase() ?: return null
        if (ext !in listOf("asm", "s", "inc")) return null

        val head = file.inputStream.bufferedReader().use {
            it.readText().take(2048)
        }

        return when {
            head.contains("section .text", ignoreCase = true) ||
                    head.contains("%macro", ignoreCase = true) ||
                    head.contains("global _start", ignoreCase = true) -> NasmFileType.INSTANCE

            head.contains("\$t0", ignoreCase = false) ||
                    head.contains(".globl main", ignoreCase = true) ||
                    head.contains("syscall", ignoreCase = true) -> MipsFileType.INSTANCE

            else -> null
        }
    }
}

