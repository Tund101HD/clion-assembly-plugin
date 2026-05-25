package me.lucaperri.dev.languages.run.toolchain

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.cmake.CMakeSettings
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains

// Detects WSL toolchain state to drive the "configure WSL" guidance balloon.
//
// Two distinct questions matter, and conflating them produced a confusing
// false-positive (the balloon fired even though a WSL toolchain existed):
//   • Does *any* WSL toolchain exist?           → hasWslToolchain()
//   • Will the project's CMake build actually    → projectUsesWslToolchain()
//     *use* a WSL toolchain?
//
// CLion builds with the toolchain selected by the active CMake profile
// (falling back to the default toolchain). A WSL toolchain that merely exists
// while the default/active profile is MinGW won't assemble — so the second
// question is the one that should gate the balloon.
//
// The Run Configurations themselves work without any of this (they shell out to
// wsl.exe directly); only CMake-managed builds and CLion's debug integration
// need a properly-selected WSL toolchain.
//
// All CIDR access is runCatching-wrapped so a future API rename degrades to
// "balloon shows" rather than a startup crash.
object WslToolchainDetector {

    private val LOG = Logger.getInstance(WslToolchainDetector::class.java)

    // Diagnostic dump (INFO) of every toolchain CLion reports, the default, and
    // each active CMake profile's toolchain. Useful for support when a WSL
    // toolchain isn't being picked up; enable INFO logging for this category to
    // see it (Help → Diagnostic Tools → Debug Log Settings).
    fun logDiagnostics(project: Project) {
        runCatching {
            val tcs  = CPPToolchains.getInstance()
            val list = tcs.toolchains.joinToString { "${it.name}[${it.toolSetKind}]" }
            val def  = tcs.defaultToolchain?.let { "${it.name}[${it.toolSetKind}]" } ?: "<none>"
            val profs = CMakeSettings.getInstance(project).activeProfiles
                .joinToString { "${it.name}->${it.toolchainName ?: "(default)"}" }
            LOG.info("[Assembly] toolchains=[$list] default=$def activeProfiles=[$profs] " +
                "usesWsl=${projectUsesWslToolchain(project)} hasWsl=${hasWslToolchain()}")
        }
    }

    fun hasWslToolchain(): Boolean = wslToolchain() != null

    // The WSL distribution name (e.g. "Ubuntu-24.04") of the toolchain the build
    // uses, so the plugin shells into the *same* distro as CMake — not the OS
    // default distro. Prefers the default toolchain when it's WSL (that's what
    // CMake falls back to), else the first registered WSL toolchain. Returns null
    // when no WSL toolchain is configured (callers fall back to the default distro).
    fun wslDistroName(): String? = runCatching {
        val tcs = CPPToolchains.getInstance()
        val tc  = tcs.defaultToolchain?.takeIf { it.toolSetKind == CPPToolSet.Kind.WSL }
            ?: wslToolchain()
        tc?.wsl?.wslDistributionName?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // Returns the first registered WSL toolchain, or null. Used both to decide
    // what guidance to show and to bind the GDB debug session to that toolchain
    // (so CLion maps /mnt/c <-> C: source paths).
    fun wslToolchain(): CPPToolchains.Toolchain? = runCatching {
        CPPToolchains.getInstance().toolchains.firstOrNull { it.toolSetKind == CPPToolSet.Kind.WSL }
    }.getOrNull()

    // True when the project's CMake build resolves to a WSL toolchain — i.e. at
    // least one active CMake profile selects a WSL toolchain (or selects nothing
    // and the default toolchain is WSL). This is what determines whether the
    // assembly build will actually work, so it's the real gate for the balloon.
    fun projectUsesWslToolchain(project: Project): Boolean = runCatching {
        val toolchains = CPPToolchains.getInstance()
        fun isWsl(tc: CPPToolchains.Toolchain?) = tc?.toolSetKind == CPPToolSet.Kind.WSL

        val profiles = CMakeSettings.getInstance(project).activeProfiles
        if (profiles.isEmpty()) {
            // No profiles resolved yet (e.g. CMake configure just failed) —
            // fall back to whether the default toolchain is WSL.
            return@runCatching isWsl(toolchains.defaultToolchain)
        }
        profiles.any { profile ->
            val name = profile.toolchainName
            val tc = if (name.isNullOrBlank()) toolchains.defaultToolchain
                     else toolchains.getToolchainByNameOrDefault(name)
            isWsl(tc)
        }
    }.getOrDefault(false)
}
