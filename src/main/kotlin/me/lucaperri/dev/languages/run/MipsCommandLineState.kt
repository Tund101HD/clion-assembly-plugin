package me.lucaperri.dev.languages.run

import me.lucaperri.dev.languages.run.toolchain.PlatformHelper

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.ParametersList
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import me.lucaperri.dev.languages.settings.AsmExecutableSettings
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

// Pipeline: mips-linux-gnu-as → mips-linux-gnu-ld → qemu-mips-static.
//
// All three tools are cross-compilation/emulation tools that run inside WSL
// on Windows (they are Linux binaries, not native Windows executables).
// PlatformHelper routes every command through `wsl.exe` on Windows.
//
// Requires inside WSL (Ubuntu/Debian):
//   sudo apt install gcc-mips-linux-gnu qemu-user-static
// That installs mips-linux-gnu-as, mips-linux-gnu-ld, and qemu-mips-static.
class MipsCommandLineState(
    private val environment: ExecutionEnvironment,
    private val config: MipsRunConfiguration
) : RunProfileState {

    private val currentProcess = AtomicReference<Process?>(null)

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val console = TextConsoleBuilderFactory.getInstance()
            .createBuilder(environment.project).console
        val handler = makeHandler(console)
        console.attachToProcess(handler)
        handler.startNotify()
        return DefaultExecutionResult(console, handler)
    }

    private fun makeHandler(console: ConsoleView): ProcessHandler = object : ProcessHandler() {
        override fun startNotify() {
            super.startNotify()
            ApplicationManager.getApplication().executeOnPooledThread {
                notifyProcessTerminated(runPipeline(console))
            }
        }
        override fun destroyProcessImpl() { currentProcess.get()?.destroy() }
        override fun detachProcessImpl() { notifyProcessDetached() }
        override fun detachIsDefault() = false
        override fun getProcessInput(): OutputStream? = null
    }

    private fun runPipeline(console: ConsoleView): Int {
        val ph       = PlatformHelper
        val settings = AsmExecutableSettings.getInstance()

        val out = MipsBuildPipeline.build(
            config.scriptName, config.workingDirectory,
            config.assemblerArgs, config.cInterop, console, settings,
            marchOverride = config.marchOverride,
        ) ?: return 1

        val workDir = config.workingDirectory.ifBlank { out.parent ?: "." }
        val emulBin = settings.mipsEmulatorPath.ifBlank { "qemu-mips-static" }

        sys(console, "Running ${out.name} via $emulBin...\n")
        return try {
            val programArgs = ParametersList()
                .also { it.addParametersString(config.programParameters) }.list
            // A MIPS ELF can't execute natively — run it under qemu-mips-static.
            val runCmd = if (ph.isWindows) {
                val esc    = ph.toWslPath(out.path).replace("'", "'\\''")
                val argStr = programArgs.joinToString(" ") { "'${it.replace("'", "'\\''")}'" }
                // `--` separates wsl.exe flags from the WSL-side command. Without
                // it complex bash tokens (here `&&`, redirects when added later)
                // can be consumed by wsl.exe instead of bash.
                ph.wslExe() + listOf("--", "bash", "-c", "chmod +x '$esc' && '$emulBin' '$esc' $argStr")
            } else {
                listOf(emulBin, out.path) + programArgs
            }
            val process = GeneralCommandLine(runCmd).withWorkDirectory(File(workDir)).createProcess()
            currentProcess.set(process)
            streamProcess(console, process).also { currentProcess.set(null) }
        } catch (e: Exception) { err(console, "Run error: ${e.message}\n"); 1 }
    }

    private fun streamProcess(console: ConsoleView, process: Process): Int {
        val t1 = Thread {
            process.inputStream.bufferedReader()
                .forEachLine { console.print(it + "\n", ConsoleViewContentType.NORMAL_OUTPUT) }
        }.also { it.isDaemon = true; it.start() }
        val t2 = Thread {
            process.errorStream.bufferedReader()
                .forEachLine { console.print(it + "\n", ConsoleViewContentType.ERROR_OUTPUT) }
        }.also { it.isDaemon = true; it.start() }
        val code = process.waitFor()
        t1.join(3000); t2.join(3000)
        sys(console, "\nProcess finished with exit code $code\n")
        return code
    }

    private fun err(c: ConsoleView, t: String) = c.print(t, ConsoleViewContentType.ERROR_OUTPUT)
    private fun sys(c: ConsoleView, t: String) = c.print(t, ConsoleViewContentType.SYSTEM_OUTPUT)
}
