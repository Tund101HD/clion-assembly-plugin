package me.lucaperri.dev.languages.run

import me.lucaperri.dev.languages.run.toolchain.PlatformHelper

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import me.lucaperri.dev.languages.settings.MipsArch
import java.io.File

// Shared build logic for all MIPS run configuration types.
// Encapsulates the assemble → link (pure ASM) or compile (C interop) steps
// so MipsCommandLineState and MipsQemuServerCommandLineState don't duplicate it.
object MipsBuildPipeline {

    // Returns the output binary File on success, null on failure.
    // All progress and errors are written to console.
    //
    // `debug = true` adds `-g` to the assembler invocation so the produced
    // ELF carries DWARF symbols for GDB. Callers that exist purely to feed
    // a debug session (e.g. MipsQemuServerCommandLineState) should set this.
    fun build(
        scriptName: String,
        workingDirectory: String,
        assemblerArgs: String,
        cInterop: Boolean,
        console: ConsoleView,
        settings: AsmExecutableSettings,
        debug: Boolean = false,
        // Per-run-config -march override. `null` (the default) inherits from
        // settings.defaultMipsArch. Allows a single run-config to target a
        // different ISA without touching the global default.
        marchOverride: MipsArch? = null,
    ): File? {
        val ph  = PlatformHelper
        val src = File(scriptName)
        if (!src.exists()) { console.err("Source file not found: $scriptName\n"); return null }

        val base    = src.nameWithoutExtension
        val srcDir  = src.parentFile ?: File(".")
        val workDir = workingDirectory.ifBlank { srcDir.path }
        // Outputs land under <workDir>/.asm-build/ instead of next to the source
        // so the run-config's quick build pipeline doesn't pollute the project
        // root with binaries and .o files. CMake's own build dir (cmake-build-*)
        // is independent.
        val outDir  = File(workDir, ".asm-build").apply { mkdirs() }
        val out     = File(outDir, base)
        val pSrc    = ph.argPath(src.path)
        val pOut    = ph.argPath(out.path)

        val march = (marchOverride ?: settings.defaultMipsArch).marchFlag
        return if (cInterop) buildCInterop(src, out, workDir, pSrc, pOut, assemblerArgs, march, console, ph, debug)
        else                 buildPureAsm(src, out, outDir, workDir, pSrc, pOut, assemblerArgs, settings, march, console, ph, debug)
    }

    private fun buildCInterop(
        src: File, out: File, workDir: String,
        pSrc: String, pOut: String,
        assemblerArgs: String,
        march: String?,
        console: ConsoleView,
        ph: PlatformHelper,
        debug: Boolean,
    ): File? {
        // mips-linux-gnu-gcc -x assembler -static <src> -o <out>
        val gccBin = AsmExecutableSettings.getInstance().mipsAsPath.ifBlank { "mips-linux-gnu-gcc" }
        console.sys(if (ph.isWindows) "Compiling ${src.name} via gcc (C interop, WSL)...\n"
                    else              "Compiling ${src.name} via gcc (C interop)...\n")
        return try {
            val extra = ParametersList().also { it.addParametersString(assemblerArgs) }.list
            val debugFlags = if (debug) listOf("-g") else emptyList()
            // Prepend -march; user-supplied `assemblerArgs` come later and gas/gcc
            // take the LAST -march, so a free-text override in assemblerArgs still
            // wins over the dropdown / global default.
            val marchFlag  = march?.let { listOf("-march=$it") } ?: emptyList()
            val args  = debugFlags + marchFlag + extra + listOf("-x", "assembler", "-static", pSrc, "-o", pOut)
            val r = CapturingProcessHandler(
                GeneralCommandLine(ph.asmCommand(gccBin, args)).withWorkDirectory(File(workDir))
            ).runProcess()
            if (r.stdout.isNotBlank()) console.print(r.stdout, ConsoleViewContentType.NORMAL_OUTPUT)
            if (r.stderr.isNotBlank()) console.err(r.stderr)
            if (r.exitCode != 0) { console.err("Compile failed (exit ${r.exitCode})\n"); null } else out
        } catch (e: Exception) { console.err("Compiler error: ${e.message}\n"); null }
    }

    private fun buildPureAsm(
        src: File, out: File, outDir: File, workDir: String,
        pSrc: String, pOut: String,
        assemblerArgs: String,
        settings: AsmExecutableSettings,
        march: String?,
        console: ConsoleView,
        ph: PlatformHelper,
        debug: Boolean,
    ): File? {
        val base  = src.nameWithoutExtension
        val obj   = File(outDir, "$base.o")
        val pObj  = ph.argPath(obj.path)
        val asBin = settings.mipsAsPath.ifBlank { "mips-linux-gnu-as" }
        val ldBin = settings.mipsLdPath.ifBlank { "mips-linux-gnu-ld" }

        console.sys(if (ph.isWindows) "Assembling ${src.name} (via WSL)...\n"
                    else              "Assembling ${src.name}...\n")
        try {
            val extra = ParametersList().also { it.addParametersString(assemblerArgs) }.list
            // `mips-linux-gnu-as -g` silently produces NO .debug_* sections —
            // GAS treats `-g` as a no-op for some target builds. `--gdwarf-2`
            // forces real DWARF emission, which gdb needs for source-level
            // stepping. Verified empirically against binutils-mips-linux-gnu
            // shipped with Ubuntu 24.04.
            val debugFlags = if (debug) listOf("--gdwarf-2") else emptyList()
            // -march goes between debug flags and user-supplied args so a free-text
            // override in assemblerArgs (last -march wins for GAS) still takes precedence.
            val marchFlag  = march?.let { listOf("-march=$it") } ?: emptyList()
            val args  = debugFlags + marchFlag + extra + listOf(pSrc, "-o", pObj)
            val r = CapturingProcessHandler(
                GeneralCommandLine(ph.asmCommand(asBin, args)).withWorkDirectory(File(workDir))
            ).runProcess()
            if (r.stdout.isNotBlank()) console.print(r.stdout, ConsoleViewContentType.NORMAL_OUTPUT)
            if (r.stderr.isNotBlank()) console.err(r.stderr)
            if (r.exitCode != 0) { console.err("Assembly failed (exit ${r.exitCode})\n"); return null }
        } catch (e: Exception) { console.err("Assembler error: ${e.message}\n"); return null }

        console.sys("Linking...\n")
        return try {
            // `-e __start` pins the entry symbol to the source-level label
            // declared in our pure-asm template. Without it, MIPS ld auto-
            // generates `_ftext` at the start of .text and resolves the entry
            // to that linker symbol — gdb then shows `_ftext` (not `__start`)
            // in stack frames and the entry breakpoint resolves on the wrong
            // symbol. Matches the user-tutorial's `-Wl,-e,__start` pattern.
            val r = CapturingProcessHandler(
                GeneralCommandLine(ph.asmCommand(ldBin, listOf("-e", "__start", pObj, "-o", pOut)))
                    .withWorkDirectory(File(workDir))
            ).runProcess()
            if (r.stdout.isNotBlank()) console.print(r.stdout, ConsoleViewContentType.NORMAL_OUTPUT)
            if (r.stderr.isNotBlank()) console.err(r.stderr)
            if (r.exitCode != 0) { console.err("Link failed (exit ${r.exitCode})\n"); null } else out
        } catch (e: Exception) { console.err("Linker error: ${e.message}\n"); null }
    }

    private fun ConsoleView.err(t: String) = print(t, ConsoleViewContentType.ERROR_OUTPUT)
    private fun ConsoleView.sys(t: String) = print(t, ConsoleViewContentType.SYSTEM_OUTPUT)
}
