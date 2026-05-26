package me.lucaperri.dev.languages.inspections

import me.lucaperri.dev.languages.run.toolchain.PlatformHelper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

// Result of looking up an assembler/linker binary for the external annotators.
// Native: a real file on the host (Windows .exe, macOS/Linux binary).
// Wsl: a command name available inside the active CLion WSL toolchain; the
// annotator must invoke it via `wsl.exe -d <distro> -- <command> …` and pass
// any source/output file paths through PlatformHelper.argPath() so they appear
// as `/mnt/c/...` to the Linux process.
internal sealed interface ResolvedTool {
    data class Native(val path: Path) : ResolvedTool
    data class Wsl(val command: String) : ResolvedTool
}

// Builds the final argv for invoking the resolved tool. Native runs the
// binary directly; Wsl prefixes with `wsl.exe -- <command>`.
internal fun ResolvedTool.commandLine(args: List<String>): List<String> = when (this) {
    is ResolvedTool.Native -> listOf(path.toString()) + args
    is ResolvedTool.Wsl    -> PlatformHelper.wslExe() + listOf("--", command) + args
}

// Resolves an external-annotator tool in this order:
//   1. The user's configured absolute path (Settings → Assembly → Executables).
//   2. The host PATH for each candidate name (.exe suffix on Windows).
//   3. Optional fallback install directories (e.g. C:\Program Files\NASM).
//   4. On Windows only: `command -v <name>` inside the active WSL toolchain
//      for each candidate, picking the first that resolves.
// Returns null if nothing matches.
internal fun resolveAsmTool(
    configuredPath: String,
    candidates: List<String>,
    fallbackDirs: List<String> = emptyList(),
): ResolvedTool? {
    if (configuredPath.isNotBlank()) {
        val p = Paths.get(configuredPath)
        if (Files.isRegularFile(p) && Files.isReadable(p)) return ResolvedTool.Native(p)
    }
    val exeSuffix = if (PlatformHelper.isWindows) ".exe" else ""
    val pathEntries = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
    for (name in candidates) {
        val exe = name + exeSuffix
        for (dir in pathEntries + fallbackDirs) {
            if (dir.isBlank()) continue
            val candidate = Paths.get(dir, exe)
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return ResolvedTool.Native(candidate)
            }
        }
    }
    if (PlatformHelper.isWindows) {
        for (name in candidates) {
            if (wslHasCommand(name)) return ResolvedTool.Wsl(name)
        }
    }
    return null
}

// Runs `wsl.exe -- command -v <name>` and reports whether the lookup
// succeeded. The 5-second timeout exists because cold WSL distros can be
// slow to launch on first call; subsequent calls return in tens of ms.
private fun wslHasCommand(name: String): Boolean = try {
    val cmd = PlatformHelper.wslExe() + listOf("--", "command", "-v", name)
    val proc = ProcessBuilder(cmd)
        .redirectErrorStream(true)
        .start()
    proc.outputStream.close()
    val finished = proc.waitFor(5, TimeUnit.SECONDS)
    if (!finished) {
        proc.destroyForcibly()
        false
    } else {
        proc.exitValue() == 0
    }
} catch (_: Exception) {
    false
}
