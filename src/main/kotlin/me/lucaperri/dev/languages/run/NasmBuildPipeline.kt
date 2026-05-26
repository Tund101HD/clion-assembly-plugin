package me.lucaperri.dev.languages.run

import me.lucaperri.dev.languages.run.toolchain.PlatformHelper

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import java.io.File

// Shared assemble+link logic for all NASM run configuration types.
// Encapsulates the nasm → gcc steps so NasmCommandLineState and
// NasmQemuServerCommandLineState don't duplicate them (mirrors MipsBuildPipeline).
//
// Linker is gcc (not raw ld) because CMake's ASM_NASM projects use
// LINKER_LANGUAGE C which invokes gcc, and it handles the object format
// and startup-code exclusion correctly across both elf32 and elf64.
//
// `debug = true` adds `-g -F dwarf` to the nasm invocation and `-g` to the
// gcc link so the produced ELF carries DWARF symbols for GDB. Callers that
// exist purely to feed a debug session (NasmQemuServerCommandLineState)
// should set this; the plain run path leaves it false for release-grade output.
object NasmBuildPipeline {

    // Returns the output binary File on success, null on failure.
    // All progress and errors are written to console.
    fun build(
        scriptName: String,
        workingDirectory: String,
        nasmFormat: String,
        cInterop: Boolean,
        console: ConsoleView,
        settings: AsmExecutableSettings,
        debug: Boolean = false,
    ): File? {
        val ph  = PlatformHelper
        val src = File(scriptName)
        if (!src.exists()) { console.err("Source file not found: $scriptName\n"); return null }

        val base    = src.nameWithoutExtension
        val srcDir   = src.parentFile ?: File(".")
        val workDir  = workingDirectory.ifBlank { srcDir.path }
        // Outputs land under <workDir>/.asm-build/ instead of next to the source
        // so the run-config's quick build pipeline doesn't pollute the project
        // root with binaries and .o files. CMake's own build dir (cmake-build-*)
        // is independent.
        val outDir   = File(workDir, ".asm-build").apply { mkdirs() }
        val obj      = File(outDir, "$base.o")
        val out      = File(outDir, base)
        val pSrc     = ph.argPath(src.path)
        val pObj     = ph.argPath(obj.path)
        val pOut     = ph.argPath(out.path)

        // Resolve binaries — on Windows these are looked up inside WSL.
        val nasmBin = settings.nasmPath.ifBlank { "nasm" }
        val gccBin  = settings.ldPath.ifBlank { "gcc" }

        // ── Step 1: Assemble ──────────────────────────────────────────────
        console.sys(if (ph.isWindows) "Assembling ${src.name} (via WSL)...\n"
                    else              "Assembling ${src.name}...\n")
        try {
            // DWARF debug info for source-level stepping; only in debug builds.
            val debugFlags = if (debug) listOf("-g", "-F", "dwarf") else emptyList()
            val args = debugFlags + listOf("-f", nasmFormat, pSrc, "-o", pObj)
            val r = CapturingProcessHandler(
                GeneralCommandLine(ph.asmCommand(nasmBin, args)).withWorkDirectory(File(workDir))
            ).runProcess()
            if (r.stdout.isNotBlank()) console.print(r.stdout, ConsoleViewContentType.NORMAL_OUTPUT)
            if (r.stderr.isNotBlank()) console.err(r.stderr)
            if (r.exitCode != 0) { console.err("Assembly failed (exit ${r.exitCode})\n"); return null }
        } catch (e: Exception) { console.err("Assembler error: ${e.message}\n"); return null }

        // ── Step 2: Link ──────────────────────────────────────────────────
        // Pure ASM: -nostdlib, _start entry point.
        // C interop: drop -nostdlib so the C runtime is linked; main() entry point.
        console.sys("Linking...\n")
        return try {
            val linkArgs = buildList {
                if (debug) add("-g")
                if (nasmFormat == "elf32") add("-m32")
                if (!cInterop) add("-nostdlib")
                // Modern gcc defaults to PIE; pure-asm objects with absolute
                // .bss/.data relocations fail with R_X86_64_32S errors otherwise.
                if (nasmFormat == "elf64" || nasmFormat == "elf32") add("-no-pie")
                add(pObj)
                add("-o"); add(pOut)
            }
            val r = CapturingProcessHandler(
                GeneralCommandLine(ph.asmCommand(gccBin, linkArgs)).withWorkDirectory(File(workDir))
            ).runProcess()
            if (r.stdout.isNotBlank()) console.print(r.stdout, ConsoleViewContentType.NORMAL_OUTPUT)
            if (r.stderr.isNotBlank()) console.err(r.stderr)
            if (r.exitCode != 0) { console.err("Link failed (exit ${r.exitCode})\n"); null } else out
        } catch (e: Exception) { console.err("Linker error: ${e.message}\n"); null }
    }

    private fun ConsoleView.err(t: String) = print(t, ConsoleViewContentType.ERROR_OUTPUT)
    private fun ConsoleView.sys(t: String) = print(t, ConsoleViewContentType.SYSTEM_OUTPUT)
}
