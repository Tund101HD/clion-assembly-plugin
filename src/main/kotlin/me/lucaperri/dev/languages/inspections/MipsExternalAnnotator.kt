package me.lucaperri.dev.languages.inspections

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import me.lucaperri.dev.languages.psi.MipsFile
import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class MipsExternalAnnotator : ExternalAnnotator<MipsExternalAnnotator.Input, List<MipsExternalAnnotator.Diag>>() {

    data class Input(val text: String)
    data class Diag(val line: Int, val severity: HighlightSeverity, val message: String)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? {
        // Intentionally ignore `hasErrors`: a single PSI parse error elsewhere in
        // the file would otherwise disable mips-as diagnostics for the whole file.
        // We hand mips-as the raw text, not the PSI tree, so localized parser
        // hiccups don't affect what mips-as sees.
        if (file !is MipsFile) return null
        return Input(file.text)
    }

    override fun doAnnotate(info: Input): List<Diag>? {
        val asm = resolveAsmTool(
            configuredPath = me.lucaperri.dev.languages.settings.AsmExecutableSettings.getInstance().mipsAsPath,
            candidates = ASSEMBLER_CANDIDATES,
        ) ?: return null
        val src = Files.createTempFile("mips-check-", ".s")
        val out = Files.createTempFile("mips-check-", ".o")
        try {
            Files.writeString(src, info.text)
            // Match the runtime build's -march so the live diagnostics don't disagree
            // with what `Build` would emit. Without this, MIPS32r2 instructions like
            // rotr/ext/seb get flagged "not available on your processor" in the editor
            // even though the actual build succeeds.
            val march = me.lucaperri.dev.languages.settings.AsmExecutableSettings
                .getInstance().defaultMipsArch.marchFlag
            // For WSL execution, file paths must be in `/mnt/c/...` form so the
            // Linux assembler can read/write them through the WSL mount.
            val srcArg = if (asm is ResolvedTool.Wsl) PlatformHelper.toWslPath(src.toString()) else src.toString()
            val outArg = if (asm is ResolvedTool.Wsl) PlatformHelper.toWslPath(out.toString()) else out.toString()
            val args = buildList {
                if (march != null) add("-march=$march")
                addAll(listOf("-o", outArg, srcArg))
            }
            val cmd = asm.commandLine(args)
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
            holder.newAnnotation(diag.severity, "mips-as: ${diag.message}")
                .range(TextRange(start, end))
                .create()
        }
    }

    // GNU as: "file.s:42: Error: unknown opcode `foo'"
    private val gnuStyle = Regex("""^.+?:(\d+):\s*(Error|Warning|Info|Note):\s*(.*)$""")

    private fun parseDiagnostics(output: String): List<Diag> =
        output.lineSequence().mapNotNull { line ->
            val m = gnuStyle.find(line) ?: return@mapNotNull null
            val lineNum = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val severity = when (m.groupValues[2].lowercase()) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WARNING
                else -> HighlightSeverity.WEAK_WARNING
            }
            Diag(lineNum, severity, m.groupValues[3].trim())
        }.toList()

    companion object {
        // Probe common cross-toolchain names. SPIM/MARS use different argument syntax so
        // we don't try them here — only GNU-style `as` programs.
        private val ASSEMBLER_CANDIDATES = listOf(
            "mips-linux-gnu-as",
            "mipsel-linux-gnu-as",
            "mips64-linux-gnu-as",
            "mips-elf-as",
            "mips-as",
        )
    }
}
