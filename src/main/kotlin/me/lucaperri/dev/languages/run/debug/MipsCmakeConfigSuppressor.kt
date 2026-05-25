package me.lucaperri.dev.languages.run.debug

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.cpp.execution.CMakeAppRunConfiguration
import java.util.concurrent.ConcurrentHashMap

// CLion auto-creates a "CMake Application" run config for every executable
// target when a CMakeLists is linked into the workspace. For MIPS targets that
// config tries to *natively execute* a MIPS ELF, which always fails — leaving a
// broken red config in the user's list. We can't run MIPS natively (that's what
// the QEMU debug config is for), so we remove the auto-created CMake App config
// for any target we've identified as MIPS.
//
// CLion re-creates these on every CMake reload, so removal must be a listener
// (fires on each add), not a one-shot sweep. All CIDR access is runCatching-
// wrapped so a future API change degrades to "config simply isn't suppressed".
class MipsCmakeConfigSuppressor(private val project: Project) : RunManagerListener {

    override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        maybeSuppress(project, settings)
    }

    companion object {
        // MIPS target names detected by AsmRunConfigurationCreator, per project.
        private val MIPS_TARGETS = Key.create<MutableSet<String>>("me.lucaperri.dev.assembly.mipsTargets")

        private fun targetSet(project: Project): MutableSet<String> {
            project.getUserData(MIPS_TARGETS)?.let { return it }
            val set = ConcurrentHashMap.newKeySet<String>()
            // putUserData isn't atomic-CAS here, but recording happens on a single
            // smart-mode pass, so a lost-update race is not a practical concern.
            project.putUserData(MIPS_TARGETS, set)
            return set
        }

        // Called by AsmRunConfigurationCreator when it detects a MIPS target.
        // Records the name and immediately sweeps any already-present CMake App
        // config for it (covers configs added before this listener saw them).
        fun recordMipsTarget(project: Project, targetName: String) {
            targetSet(project).add(targetName)
            runCatching {
                RunManager.getInstance(project).allSettings.forEach { maybeSuppress(project, it) }
            }
        }

        private fun maybeSuppress(project: Project, settings: RunnerAndConfigurationSettings) {
            runCatching {
                val cfg = settings.configuration as? CMakeAppRunConfiguration ?: return
                val targetName = cfg.cMakeTarget?.name ?: cfg.name
                if (targetName !in targetSet(project)) return
                ApplicationManager.getApplication().invokeLater {
                    runCatching { RunManager.getInstance(project).removeConfiguration(settings) }
                }
            }
        }
    }
}
