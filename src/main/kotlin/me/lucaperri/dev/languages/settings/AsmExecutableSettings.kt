package me.lucaperri.dev.languages.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

// Application-level persisted settings for the Assembly plugin.
//
// Storage name and class name are intentionally preserved across the
// settings revamp so existing user state in advancedAssembly.xml keeps
// loading. New fields are additive — missing entries fall back to defaults.
@Service(Service.Level.APP)
@State(name = "AsmExecutableSettings", storages = [Storage("advancedAssembly.xml")])
class AsmExecutableSettings : PersistentStateComponent<AsmExecutableSettings.State> {

    data class State(
        // Existing executable paths — empty means "probe PATH".
        var nasmPath: String = "",
        var ldPath: String = "",
        var mipsAsPath: String = "",
        var mipsLdPath: String = "",
        var mipsEmulatorPath: String = "",
        // New: GDB path (consumed by upcoming CIDR integration).
        var gdbPath: String = "",
        // General preferences.
        var platformOverride: PlatformOverride = PlatformOverride.AUTO,
        var defaultNasmArch: NasmArch = NasmArch.X64,
        var defaultMipsArch: MipsArch = MipsArch.MIPS32R2,
        var defaultProjectType: ProjectType = ProjectType.NASM,
        // Debugger scaffolding — persisted but not yet consumed.
        var autoLaunchGdbOnQemu: Boolean = true,
        var qemuX86_64Path: String = "",
        var preferQemuForNasmDebug: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(s: State) { state = s }

    var nasmPath: String
        get() = state.nasmPath
        set(value) { state.nasmPath = value }

    var ldPath: String
        get() = state.ldPath
        set(value) { state.ldPath = value }

    var mipsAsPath: String
        get() = state.mipsAsPath
        set(value) { state.mipsAsPath = value }

    var mipsLdPath: String
        get() = state.mipsLdPath
        set(value) { state.mipsLdPath = value }

    var mipsEmulatorPath: String
        get() = state.mipsEmulatorPath
        set(value) { state.mipsEmulatorPath = value }

    var gdbPath: String
        get() = state.gdbPath
        set(value) { state.gdbPath = value }

    var platformOverride: PlatformOverride
        get() = state.platformOverride
        set(value) { state.platformOverride = value }

    var defaultNasmArch: NasmArch
        get() = state.defaultNasmArch
        set(value) { state.defaultNasmArch = value }

    var defaultMipsArch: MipsArch
        get() = state.defaultMipsArch
        set(value) { state.defaultMipsArch = value }

    var defaultProjectType: ProjectType
        get() = state.defaultProjectType
        set(value) { state.defaultProjectType = value }

    var autoLaunchGdbOnQemu: Boolean
        get() = state.autoLaunchGdbOnQemu
        set(value) { state.autoLaunchGdbOnQemu = value }

    var qemuX86_64Path: String
        get() = state.qemuX86_64Path
        set(value) { state.qemuX86_64Path = value }

    var preferQemuForNasmDebug: Boolean
        get() = state.preferQemuForNasmDebug
        set(value) { state.preferQemuForNasmDebug = value }

    companion object {
        fun getInstance(): AsmExecutableSettings =
            ApplicationManager.getApplication().getService(AsmExecutableSettings::class.java)
    }
}
