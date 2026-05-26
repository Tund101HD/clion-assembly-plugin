package me.lucaperri.dev.languages.inspections

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import me.lucaperri.dev.languages.psi.NasmFile
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class NasmExternalAnnotator : ExternalAnnotator<NasmExternalAnnotator.Input, List<NasmExternalAnnotator.Diag>>() {

    data class Input(val text: String, val format: String)
    data class Diag(val line: Int, val severity: HighlightSeverity, val message: String)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? {
        if (hasErrors) return null
        if (file !is NasmFile) return null
        return Input(file.text, detectFormat(file))
    }

    override fun doAnnotate(info: Input): List<Diag>? {
        val nasm = resolveAsmTool(
            configuredPath = AsmExecutableSettings.getInstance().nasmPath,
            candidates = listOf("nasm"),
            fallbackDirs = WINDOWS_NASM_FALLBACK_DIRS,
        ) ?: return null
        val src = Files.createTempFile("nasm-check-", ".asm")
        val out = Files.createTempFile("nasm-check-", ".out")
        try {
            Files.writeString(src, info.text)
            // For WSL execution, file paths must be in `/mnt/c/...` form so the
            // Linux nasm process can read/write them through the WSL mount.
            val srcArg = if (nasm is ResolvedTool.Wsl) PlatformHelper.toWslPath(src.toString()) else src.toString()
            val outArg = if (nasm is ResolvedTool.Wsl) PlatformHelper.toWslPath(out.toString()) else out.toString()
            // Let nasm's default warning set through. Warnings like number-overflow
            // (`db 300`, `mov al, 300`, etc.) are exactly the diagnostics that
            // complement the PSI inspections — silencing them with `-w-all` made
            // the annotator surface only hard errors and missed most overflow cases.
            val cmd = nasm.commandLine(listOf(
                "-f", info.format,
                "-Xgnu",
                srcArg,
                "-o", outArg,
            ))
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            if (!proc.waitFor(10, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return null
            }
            return parseDiagnostics(output)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { Files.deleteIfExists(src) }
            runCatching { Files.deleteIfExists(out) }
        }
    }

    override fun apply(file: PsiFile, result: List<Diag>, holder: AnnotationHolder) {
        val doc = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        for (diag in result) {
            val lineIdx = diag.line - 1
            if (lineIdx < 0 || lineIdx >= doc.lineCount) continue
            val start = doc.getLineStartOffset(lineIdx)
            val end = doc.getLineEndOffset(lineIdx)
            holder.newAnnotation(diag.severity, "nasm: ${diag.message}")
                .range(TextRange(start, end))
                .create()
        }
    }

    private fun parseDiagnostics(output: String): List<Diag> {
        val regex = Regex("""^.+?:(\d+):(?:\s*\d+:)?\s*(error|warning|note):\s*(.*)$""")
        return output.lineSequence().mapNotNull { line ->
            val m = regex.find(line) ?: return@mapNotNull null
            val lineNum = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val severity = when (m.groupValues[2]) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WARNING
                else -> HighlightSeverity.WEAK_WARNING
            }
            Diag(lineNum, severity, m.groupValues[3].trim())
        }.toList()
    }

    // Picks the `-f` format used to validate this file. Resolution order:
    //   1. The file's own `BITS 32/64` (or `[BITS …]`) directive — the source
    //      is the single authoritative declaration of its mode.
    //   2. The nearest CMakeLists.txt's `-f elf{32,64}` (or other NASM format).
    //   3. The user's "Default NASM arch" setting.
    private fun detectFormat(file: PsiFile): String {
        RE_NASM_BITS.find(file.text)?.groupValues?.getOrNull(1)?.let { bits ->
            return if (bits == "64") "elf64" else "elf32"
        }
        cmakeFormat(file)?.let { return it }
        return runCatching { AsmExecutableSettings.getInstance().defaultNasmArch.nasmFormat }
            .getOrDefault("elf64")
    }

    // Walks up from the file's directory to find a CMakeLists.txt and reads the
    // `-f <fmt>` arg out of it. Stops at the project base path so we don't read
    // an unrelated file outside the project.
    private fun cmakeFormat(file: PsiFile): String? {
        val vfile: VirtualFile = file.virtualFile ?: return null
        val base = file.project.basePath
        var dir = vfile.parent
        repeat(10) {
            val d = dir ?: return null
            if (base != null && !d.path.startsWith(base)) return null
            val cmake = d.findChild("CMakeLists.txt")
            if (cmake != null) {
                val text = runCatching { cmake.contentsToByteArray().toString(Charsets.UTF_8) }.getOrNull()
                if (text != null) {
                    RE_NASM_FORMAT.find(text)?.groupValues?.getOrNull(1)?.let { return it }
                }
                return null
            }
            dir = d.parent
        }
        return null
    }

    companion object {
        // Windows install locations to scan when nasm is not on the user's PATH.
        // The official NASM installer drops here but doesn't update PATH by default;
        // Chocolatey/Scoop installs are already on PATH so don't need entries.
        private val WINDOWS_NASM_FALLBACK_DIRS: List<String> = listOfNotNull(
            "C:\\Program Files\\NASM",
            "C:\\Program Files (x86)\\NASM",
            System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\NASM" },
        )

        // NASM mode directive: `BITS 32`, `BITS 64`, or bracketed `[BITS 32]`.
        // First match wins; if a file later switches mode mid-stream, the file's
        // primary mode is the one declared at the top.
        private val RE_NASM_BITS = Regex(
            """^[ \t]*\[?\s*BITS\s+(16|32|64)\b""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        // Mirrors AsmRunConfigurationCreator.RE_NASM_FORMAT.
        private val RE_NASM_FORMAT = Regex(
            """-f\s+(elf64|elf32|macho64|win64|win32)""", RegexOption.IGNORE_CASE)
    }
}
