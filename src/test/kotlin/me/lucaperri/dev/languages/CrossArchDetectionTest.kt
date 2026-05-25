package me.lucaperri.dev.languages

import junit.framework.TestCase
import me.lucaperri.dev.languages.run.AsmRunConfigurationCreator
import me.lucaperri.dev.languages.run.toolchain.WslToolchainProbe
import me.lucaperri.dev.languages.settings.AsmExecutableSettings

// Pure-logic coverage for the Phase 8.4 cross-arch NASM debug decision and the
// toolchain-probe capability mapping. No IDE fixture needed.
class CrossArchDetectionTest : TestCase() {

    // ── isArmArch ────────────────────────────────────────────────────────────

    fun testArmArchRecognisesAarch64() {
        assertTrue(AsmRunConfigurationCreator.isArmArch("aarch64"))
        assertTrue(AsmRunConfigurationCreator.isArmArch("arm64"))
        assertTrue(AsmRunConfigurationCreator.isArmArch("ARM")) // case-insensitive
    }

    fun testArmArchRejectsX86() {
        assertFalse(AsmRunConfigurationCreator.isArmArch("amd64"))
        assertFalse(AsmRunConfigurationCreator.isArmArch("x86_64"))
        assertFalse(AsmRunConfigurationCreator.isArmArch(null))
    }

    // ── nasmQemuDecision ──────────────────────────────────────────────────────

    fun testQemuOnArmNonMac() {
        assertTrue(AsmRunConfigurationCreator.nasmQemuDecision(prefer = false, isMac = false, isArm = true))
    }

    fun testNoQemuOnArmMac() {
        // macOS is deferred — ARM alone must not trigger QEMU there.
        assertFalse(AsmRunConfigurationCreator.nasmQemuDecision(prefer = false, isMac = true, isArm = true))
    }

    fun testNoQemuOnX86WithoutPreference() {
        assertFalse(AsmRunConfigurationCreator.nasmQemuDecision(prefer = false, isMac = false, isArm = false))
    }

    fun testPreferenceForcesQemuEverywhere() {
        assertTrue(AsmRunConfigurationCreator.nasmQemuDecision(prefer = true, isMac = false, isArm = false))
        assertTrue(AsmRunConfigurationCreator.nasmQemuDecision(prefer = true, isMac = true, isArm = true))
    }

    // ── Capability tool mapping ───────────────────────────────────────────────

    fun testNasmDebugNeedsQemuAndMultiarchGdb() {
        val tools = WslToolchainProbe.Capability.NASM_DEBUG.tools
        assertTrue(tools.contains(WslToolchainProbe.Tool.QEMU_X86_64))
        assertTrue(tools.contains(WslToolchainProbe.Tool.GDB_MULTIARCH))
    }

    fun testMipsDebugToolsBranchOnInterop() {
        val cInterop = WslToolchainProbe.mipsDebugTools(cInterop = true)
        assertTrue(cInterop.contains(WslToolchainProbe.Tool.MIPS_GCC))
        assertFalse(cInterop.contains(WslToolchainProbe.Tool.MIPS_AS))

        val pureAsm = WslToolchainProbe.mipsDebugTools(cInterop = false)
        assertTrue(pureAsm.contains(WslToolchainProbe.Tool.MIPS_AS))
        assertTrue(pureAsm.contains(WslToolchainProbe.Tool.MIPS_LD))
        assertFalse(pureAsm.contains(WslToolchainProbe.Tool.MIPS_GCC))

        // Both must still pull in the emulator and a multiarch-capable gdb.
        for (tools in listOf(cInterop, pureAsm)) {
            assertTrue(tools.contains(WslToolchainProbe.Tool.QEMU_MIPS))
            assertTrue(tools.contains(WslToolchainProbe.Tool.GDB_MULTIARCH))
        }
    }

    // ── Tool.resolvedName respects explicit paths ─────────────────────────────

    fun testResolvedNameUsesExplicitPathThenFallsBackToBin() {
        val settings = AsmExecutableSettings()
        assertEquals("nasm", WslToolchainProbe.Tool.NASM.resolvedName(settings))
        settings.nasmPath = "/opt/nasm/bin/nasm"
        assertEquals("/opt/nasm/bin/nasm", WslToolchainProbe.Tool.NASM.resolvedName(settings))
    }
}
