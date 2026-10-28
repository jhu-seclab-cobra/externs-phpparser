package edu.jhu.cobra.externs.phpparser.binary

import io.mockk.EqMatcher
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.io.Closeable
import java.nio.file.Path

private const val PROCESS_POLL_ATTEMPTS = 50
private const val PROCESS_POLL_INTERVAL_MILLIS = 100L

/*
 * Shared fixture binaries for [AbcBinary] tests.
 *
 * - [EchoBinary] — echoes a message; exercises Argument/Option delegates and command assembly.
 * - [SleepBinary] — sleeps; tests override the execution backstop to observe timeouts quickly.
 * - [FailBinary] — exits non-zero; exercises failure paths.
 * - [StubbornBinary] — owns its real Process while awaiting readiness and running the short backstop.
 * - [ReadyProcess] — reaps the owned process on every exit, including readiness errors.
 * - [MissingBinary] — command names a nonexistent executable; exercises the spawn-failure path.
 * - [awaitProcess] — waits for the fixture child's own readiness PID.
 * - [isProcessAlive] — probes only that owned PID and rejects probe failures.
 */

internal class EchoBinary : AbcBinary() {
    var message: String by Argument<String>("message")
    var nullableArg: String? by Argument<String?>("nullableArg")
    var verbose: Boolean by Option("--verbose", false)
    var outputFormat: String by Option("--format", "text")
    var nullableOpt: String? by Option<String?>("--nullable")

    override fun getCommandArray(): Array<String> =
        buildList {
            add("echo")
            for ((key, value) in allOptions) if (value is Boolean && value) add(key)
            add(message)
        }.toTypedArray()
}

internal class SleepBinary(
    private val seconds: Int = 60,
    override val executionTimeoutMillis: Long = EXECUTION_TIMEOUT_MILLIS,
) : AbcBinary() {
    override fun getCommandArray(): Array<String> = arrayOf("sleep", seconds.toString())
}

internal class FailBinary : AbcBinary() {
    override fun getCommandArray(): Array<String> = arrayOf("false")
}

internal class MissingBinary : AbcBinary() {
    override fun getCommandArray(): Array<String> = arrayOf("/nonexistent-cobra-binary-xyz")
}

// Publishes its own PID after installing the trap so tests can check that exact process.
internal open class StubbornBinary(
    private val pidFile: Path,
    timeoutMillis: Long,
    override val terminationGraceMillis: Long,
) : AbcBinary() {
    private val script = "trap '' TERM; printf '%s' \"\$\$\" > \"\$1\"; while :; do sleep 1; done"

    override val executionTimeoutMillis: Long = timeoutMillis

    override fun execute(): BinaryResult {
        val command = getCommandArray()
        mockkConstructor(ProcessBuilder::class)
        try {
            return ReadyProcess(pidFile).use { owned ->
                every { constructedWith<ProcessBuilder>(EqMatcher(command)).start() } answers {
                    owned.started(callOriginal())
                }
                super.execute()
            }
        } catch (interrupt: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupt
        } finally {
            unmockkConstructor(ProcessBuilder::class)
        }
    }

    override fun getCommandArray(): Array<String> = arrayOf("sh", "-c", script, "cobra-test", pidFile.toString())
}

// Retains the actual child before readiness can fail; normal timeout handling still belongs to AbcBinary.
private class ReadyProcess(
    private val pidFile: Path,
) : Closeable {
    private var process: Process? = null

    fun started(child: Process): Process {
        check(process == null) { "The fixture must own only one process" }
        process = child
        awaitProcess(pidFile)
        return child
    }

    override fun close() {
        val child = process ?: return
        if (child.isAlive) child.destroyForcibly().waitFor()
    }
}

// The child publishes its PID only after it is ready to ignore TERM.
internal fun awaitProcess(pidFile: Path): String {
    repeat(PROCESS_POLL_ATTEMPTS) {
        val pid = if (pidFile.toFile().exists()) pidFile.toFile().readText(Charsets.UTF_8) else ""
        if (pid.toLongOrNull() != null && isProcessAlive(pid)) return pid
        Thread.sleep(PROCESS_POLL_INTERVAL_MILLIS)
    }
    error("process did not publish a live PID at $pidFile")
}

internal fun isProcessAlive(pid: String): Boolean {
    require(pid.toLong() > 0)
    val command = ProcessBuilder("kill", "-0", pid).redirectErrorStream(true)
    command.environment()["LC_ALL"] = "C"
    val probe = command.start()
    val output = probe.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    val code = probe.waitFor()
    check(code == 0 || "No such process" in output) { "Cannot inspect owned process $pid: $output" }
    return code == 0
}
