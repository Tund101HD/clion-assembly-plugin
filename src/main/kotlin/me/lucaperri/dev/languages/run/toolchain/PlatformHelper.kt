package me.lucaperri.dev.languages.run.toolchain

import com.intellij.openapi.util.SystemInfo
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import me.lucaperri.dev.languages.settings.PlatformOverride

// Centralises platform detection and WSL command construction.
//
// On Windows, the assembler/linker/emulator all live inside WSL — they are
// not native Windows binaries. Every command must be prefixed with
// `wsl.exe --` (or `wsl.exe bash -c`) and every file path must be converted
// from a Windows path (C:\...) to its WSL mount path (/mnt/c/...).
//
// On Linux/macOS, commands run natively and paths are unchanged.
//
// The detected OS can be overridden via Settings → Assembly → General
// (Platform override). The override is consulted on every read so toggling
// it takes effect without restart. If the settings service isn't yet
// available (e.g. very early startup or unit tests with no Application),
// detection falls back to SystemInfo.
object PlatformHelper {

    private enum class Os { WINDOWS, MAC, LINUX }

    private fun detected(): Os = when {
        SystemInfo.isWindows -> Os.WINDOWS
        SystemInfo.isMac     -> Os.MAC
        else                 -> Os.LINUX
    }

    private fun resolve(): Os {
        val override = runCatching { AsmExecutableSettings.getInstance().platformOverride }
            .getOrNull() ?: PlatformOverride.AUTO
        return when (override) {
            PlatformOverride.AUTO    -> detected()
            PlatformOverride.WINDOWS -> Os.WINDOWS
            PlatformOverride.LINUX   -> Os.LINUX
            PlatformOverride.MACOS   -> Os.MAC
        }
    }

    val isWindows: Boolean get() = resolve() == Os.WINDOWS
    val isMac: Boolean     get() = resolve() == Os.MAC
    val isLinux: Boolean   get() = resolve() == Os.LINUX

    // C:\Users\foo\bar.nasm  →  /mnt/c/Users/foo/bar.nasm
    fun toWslPath(windowsPath: String): String {
        val p = windowsPath.replace('\\', '/')
        return if (p.length >= 2 && p[1] == ':') {
            "/mnt/${p[0].lowercaseChar()}${p.substring(2)}"
        } else p
    }

    // Returns the path to use as a command-line argument to a process that
    // runs inside WSL (Windows) or natively (Linux/macOS).
    fun argPath(hostPath: String): String =
        if (isWindows) toWslPath(hostPath) else hostPath

    // The `wsl.exe` invocation prefix. Targets the active CLion WSL toolchain's
    // distribution (so the plugin uses the *same* distro as CMake builds) when one
    // is configured, and falls back to the OS-default distro otherwise. All WSL
    // command construction in the plugin goes through this.
    //   → ["wsl.exe", "-d", "Ubuntu-24.04"]  or  ["wsl.exe"]
    fun wslExe(): List<String> {
        val distro = WslToolchainDetector.wslDistroName()
        return if (distro != null) listOf("wsl.exe", "-d", distro) else listOf("wsl.exe")
    }

    // Wraps a tool invocation so it runs inside WSL on Windows.
    // On Linux/macOS, returns the args unchanged.
    //   asmCommand("nasm", listOf("-felf64", src, "-o", obj))
    //     → Windows: ["wsl.exe", "-d", <distro>, "--", "nasm", "-felf64", ...]
    //     → Linux:   ["nasm", "-felf64", ...]
    fun asmCommand(bin: String, args: List<String>): List<String> =
        if (isWindows) wslExe() + listOf("--", bin) + args
        else listOf(bin) + args

    // For the final "run" step we need chmod+x to work inside WSL before
    // executing, because /mnt/c files don't always have the execute bit set
    // in the WSL view of the Windows filesystem.
    fun runCommand(binaryPath: String, programArgs: List<String>): List<String> {
        return if (isWindows) {
            val wslPath = toWslPath(binaryPath)
            val escapedPath = wslPath.replace("'", "'\\''")
            val argStr = programArgs.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
            // `--` separates wsl.exe's own flags from the command run inside
            // WSL. Without it complex bash tokens get misparsed by wsl.exe.
            wslExe() + listOf("--", "bash", "-c", "chmod +x '$escapedPath' && '$escapedPath' $argStr")
        } else {
            listOf(binaryPath) + programArgs
        }
    }

    // Human-readable description of the resolved platform, shown in Settings.
    // Indicates whether the OS is auto-detected or user-overridden.
    fun description(): String {
        val resolved = resolve()
        val os = when (resolved) {
            Os.WINDOWS -> "Windows (WSL required)"
            Os.MAC     -> "macOS"
            Os.LINUX   -> "Linux"
        }
        val arch = System.getProperty("os.arch") ?: "unknown"
        val override = runCatching { AsmExecutableSettings.getInstance().platformOverride }
            .getOrNull() ?: PlatformOverride.AUTO
        val suffix = if (override == PlatformOverride.AUTO) "" else " (override)"
        return "$os · $arch$suffix"
    }
}
