package me.lucaperri.dev.languages.run.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

// Verifies that the tools a given build/debug path needs are actually present
// in the toolchain the plugin shells into — `command -v <tool>` inside WSL on
// Windows, or natively on Linux/macOS.
//
// A *registered* WSL toolchain (WslToolchainDetector) says nothing about whether
// nasm / gdb-multiarch / qemu-user-static are installed in it; this probe closes
// that gap so users get an actionable "sudo apt install …" instead of a raw
// `command not found` mid-build.
//
// Probing targets the *default* WSL distro (bare `wsl.exe`), matching the
// environment the plugin's standalone run/debug configs actually execute in.
// (CLion's CMake builds may use a different registered-toolchain distro; that
// path is validated by CLion itself.)
object WslToolchainProbe {

    private val LOG = Logger.getInstance(WslToolchainProbe::class.java)

    // Builds a multi-line shell script that runs `<perTool>` for each tool name.
    // We hardcode the names directly (single-quoted, with literal-quote escaping)
    // instead of passing them as positional args, because passing `bash -c <script>
    // _ <names...>` through `wsl.exe -d <distro> --` has been observed to swallow
    // the positionals in some Windows arg-passing paths. One self-contained script
    // arg keeps the contract simple and removes that failure mode.
    private fun perToolScript(names: List<String>, perTool: (String) -> String): String =
        names.joinToString("\n") { perTool("'${it.replace("'", "'\\''")}'") }

    // Where commands actually run, for the diagnostic log line.
    private fun wslPrefix(): String =
        if (PlatformHelper.isWindows) PlatformHelper.wslExe().joinToString(" ") else "<native>"

    // A single tool: the binary name probed on PATH, the apt package that
    // provides it, and the optional explicit path the user may have set
    // (when set, that path is probed instead of the bare name).
    enum class Tool(
        val bin: String,
        val aptPackage: String,
        val pathOf: (AsmExecutableSettings) -> String,
    ) {
        NASM         ("nasm",               "nasm",                     { it.nasmPath }),
        GCC          ("gcc",                "gcc",                      { it.ldPath }),
        QEMU_X86_64  ("qemu-x86_64-static", "qemu-user-static",         { it.qemuX86_64Path }),
        QEMU_MIPS    ("qemu-mips-static",   "qemu-user-static",         { it.mipsEmulatorPath }),
        MIPS_GCC     ("mips-linux-gnu-gcc", "gcc-mips-linux-gnu",       { it.mipsAsPath }),
        MIPS_AS      ("mips-linux-gnu-as",  "binutils-mips-linux-gnu",  { it.mipsAsPath }),
        MIPS_LD      ("mips-linux-gnu-ld",  "binutils-mips-linux-gnu",  { it.mipsLdPath }),
        // gdb-multiarch is required for *both* MIPS and cross-arch x86-64 debug:
        // a host-native gdb (e.g. ARM gdb) can't speak the foreign target's
        // architecture over the gdbstub.
        GDB_MULTIARCH("gdb-multiarch",      "gdb-multiarch",            { it.gdbPath });

        fun resolvedName(settings: AsmExecutableSettings): String =
            pathOf(settings).ifBlank { bin }
    }

    // Representative tool sets per path, used by the "Verify Toolchain" overview.
    // (MIPS build is proxied by the gcc cross-compiler, which pulls in binutils.)
    enum class Capability(val label: String, val tools: List<Tool>) {
        NASM_BUILD("NASM build",             listOf(Tool.NASM, Tool.GCC)),
        NASM_DEBUG("NASM cross-arch debug",  listOf(Tool.NASM, Tool.GCC, Tool.QEMU_X86_64, Tool.GDB_MULTIARCH)),
        MIPS_BUILD("MIPS build",             listOf(Tool.MIPS_GCC)),
        MIPS_DEBUG("MIPS debug",             listOf(Tool.MIPS_GCC, Tool.QEMU_MIPS, Tool.GDB_MULTIARCH)),
    }

    // Tool list for a MIPS debug session, narrowed by how the binary is built:
    // C-interop uses the gcc driver, pure-asm uses as + ld.
    fun mipsDebugTools(cInterop: Boolean): List<Tool> = buildList {
        if (cInterop) add(Tool.MIPS_GCC) else { add(Tool.MIPS_AS); add(Tool.MIPS_LD) }
        add(Tool.QEMU_MIPS)
        add(Tool.GDB_MULTIARCH)
    }

    // Probes the given tools in a single shell invocation. Returns a map of
    // tool → present. On any failure (no WSL, timeout) every tool maps to false.
    fun probe(tools: List<Tool>, settings: AsmExecutableSettings): Map<Tool, Boolean> {
        if (tools.isEmpty()) return emptyMap()
        val names = tools.map { it.resolvedName(settings) }
        val script = perToolScript(names) { q -> "command -v $q >/dev/null 2>&1 && echo 1 || echo 0" }
        val cmd = if (PlatformHelper.isWindows)
            PlatformHelper.wslExe() + listOf("--", "bash", "-c", script)
        else
            listOf("bash", "-c", script)

        return runCatching {
            val r = CapturingProcessHandler(GeneralCommandLine(cmd)).runProcess(15_000)
            val lines = r.stdout.lines().filter { it.isNotBlank() }
            LOG.info("[Assembly] probe via ${wslPrefix()} exit=${r.exitCode} tools=${names.size} lines=${lines.size}" +
                if (r.stderr.isNotBlank()) " stderr=[${r.stderr.trim()}]" else "")
            tools.mapIndexed { i, tool -> tool to (lines.getOrNull(i)?.trim() == "1") }.toMap()
        }.getOrElse {
            LOG.warn("[Assembly] probe failed via ${wslPrefix()}", it)
            tools.associateWith { false }
        }
    }

    // Resolves the absolute path of each tool (via `command -v`, which searches
    // PATH — i.e. the standard install locations /usr/bin, /usr/local/bin, …).
    // Returns tool → absolute path, or "" when not found. Lets the Verify action
    // auto-fill discovered paths into the settings fields.
    fun resolvePaths(tools: List<Tool>, settings: AsmExecutableSettings): Map<Tool, String> {
        if (tools.isEmpty()) return emptyMap()
        val names = tools.map { it.resolvedName(settings) }
        // One line per tool: the resolved path, or empty when not found.
        val script = perToolScript(names) { q -> "command -v $q 2>/dev/null || echo" }
        val cmd = if (PlatformHelper.isWindows)
            PlatformHelper.wslExe() + listOf("--", "bash", "-c", script)
        else
            listOf("bash", "-c", script)

        return runCatching {
            val r = CapturingProcessHandler(GeneralCommandLine(cmd)).runProcess(15_000)
            // Split on \n only: an empty line means "tool not found". Don't strip
            // empty lines — that would misalign indices.
            val lines = r.stdout.replace("\r", "").split("\n")
            val map = tools.mapIndexed { i, tool -> tool to lines.getOrNull(i)?.trim().orEmpty() }.toMap()
            val found = map.values.count { it.isNotEmpty() }
            LOG.info("[Assembly] resolvePaths via ${wslPrefix()} exit=${r.exitCode} tools=${names.size} found=$found" +
                if (r.stderr.isNotBlank()) " stderr=[${r.stderr.trim()}]" else "")
            if (found == 0 && r.stdout.isNotBlank()) {
                LOG.info("[Assembly] resolvePaths raw stdout=[${r.stdout.take(500)}]")
            }
            map
        }.getOrElse {
            LOG.warn("[Assembly] resolvePaths failed via ${wslPrefix()}", it)
            emptyMap()
        }
    }

    // The one-shot apt command that installs every tool the plugin can use
    // (both NASM and MIPS, build and debug). Offered as a copy button in settings.
    fun fullInstallCommand(): String =
        "sudo apt install -y " + Tool.values().map { it.aptPackage }.distinct().joinToString(" ")

    // Synchronous preflight for a debug launch. Probes [tools]; if any are
    // missing, prints an actionable message to [console] and returns false.
    // Must be called off the EDT (the QEMU command-line states run on a pool).
    fun preflight(
        tools: List<Tool>,
        label: String,
        settings: AsmExecutableSettings,
        console: ConsoleView,
    ): Boolean {
        val results = probe(tools, settings)
        val missing = tools.filter { results[it] == false }
        if (missing.isEmpty()) return true

        val where = if (PlatformHelper.isWindows) "in your WSL distribution" else "on your system"
        err(console, "\nMissing toolchain components for $label $where:\n")
        missing.forEach { err(console, "  • ${it.resolvedName(settings)}\n") }
        val packages = missing.map { it.aptPackage }.distinct().joinToString(" ")
        sys(console, "\nInstall them with:\n  sudo apt install $packages\n")
        sys(console, "(Or set explicit paths in Settings → Assembly → Executables.)\n\n")
        return false
    }

    // Convenience overload for paths with a fixed representative tool set.
    fun preflight(
        capability: Capability,
        settings: AsmExecutableSettings,
        console: ConsoleView,
    ): Boolean = preflight(capability.tools, capability.label, settings, console)

    private fun err(c: ConsoleView, t: String) = c.print(t, ConsoleViewContentType.ERROR_OUTPUT)
    private fun sys(c: ConsoleView, t: String) = c.print(t, ConsoleViewContentType.SYSTEM_OUTPUT)
}
