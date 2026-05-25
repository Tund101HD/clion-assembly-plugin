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

// Pipeline: nasm → gcc -nostdlib (linker driver, as recommended by the
// CLion/WSL guide) → run binary.
//
// On Windows everything is routed through WSL via PlatformHelper.
// On Linux/macOS the commands run natively.
//
// Linker is gcc (not raw ld) because CMake's ASM_NASM projects use
// LINKER_LANGUAGE C which invokes gcc, and it handles the object format
// and startup-code exclusion correctly across both elf32 and elf64.
class NasmCommandLineState(
    private val environment: ExecutionEnvironment,
    private val config: NasmRunConfiguration
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
        val ph = PlatformHelper
        val settings = AsmExecutableSettings.getInstance()

        // ── Steps 1–2: Assemble + link (shared with the QEMU debug path) ──
        // debug = false: the plain run path produces release-grade output
        // with no DWARF symbols.
        val out = NasmBuildPipeline.build(
            config.scriptName, config.workingDirectory,
            config.nasmFormat, config.cInterop, console, settings,
            debug = false,
        ) ?: return 1

        val workDir = config.workingDirectory.ifBlank { out.parent ?: "." }

        // ── Step 3: Run ───────────────────────────────────────────────────
        sys(console, "Running ${out.name}...\n")
        return try {
            val programArgs = ParametersList()
                .also { it.addParametersString(config.programParameters) }.list
            val runCmd = ph.runCommand(out.path, programArgs)
            val process = GeneralCommandLine(runCmd)
                .withWorkDirectory(File(workDir))
                .createProcess()
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
