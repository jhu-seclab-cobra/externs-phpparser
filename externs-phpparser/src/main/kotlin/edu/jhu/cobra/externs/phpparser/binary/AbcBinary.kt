package edu.jhu.cobra.externs.phpparser.binary

import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.notExists

// Liveness backstop for a wedged external process; a bound on hangs, not a tuning knob.
internal const val EXECUTION_TIMEOUT_MILLIS = 60_000L

// Grace period for a destroyed process to exit before forcible termination.
internal const val TERMINATION_GRACE_MILLIS = 5_000L

// Collision-free identifier for a command line: SHA-1 over the NUL-joined argument bytes.
private fun cacheKeyOf(cmdArray: Array<String>): String {
    val cmdBytes = cmdArray.joinToString(separator = "\u0000").toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-1").digest(cmdBytes)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/** Abstract executable that runs in a working directory; a subclass assembles its command line. */
public abstract class AbcBinary {
    public var workTmpDir: Path =
        Path(System.getProperty("java.io.tmpdir")) / "cobra" / "binaries" / this::class.java.simpleName
    public var doCacheOutput: Boolean = false

    // Liveness backstops. Open so test doubles can shorten waits; never per-run configuration.
    internal open val executionTimeoutMillis: Long = EXECUTION_TIMEOUT_MILLIS
    internal open val terminationGraceMillis: Long = TERMINATION_GRACE_MILLIS

    /** Builds the CLI command from the subclass's current configuration. */
    public abstract fun getCommandArray(): Array<String>

    /**
     * Runs the configured command and returns the result.
     * @return exit code and output file.
     */
    public open fun execute(): BinaryResult {
        if (workTmpDir.notExists()) workTmpDir.createDirectories()
        val cmdArray = this.getCommandArray()
        val cmdUname = cacheKeyOf(cmdArray)
        val cacheFile = workTmpDir.resolve(".$cmdUname.cache")
        if (doCacheOutput && cacheFile.exists()) return BinaryResult(code = 0, output = cacheFile.toFile())
        val result = runOnce(cmdArray, cmdUname)
        if (doCacheOutput && result.code == 0) {
            result.output.toPath().moveTo(cacheFile, overwrite = true)
            return BinaryResult(code = 0, output = cacheFile.toFile())
        }
        return result
    }

    // Spawns the command into a per-execution output file and waits within the liveness backstop.
    private fun runOnce(
        cmdArray: Array<String>,
        cmdUname: String,
    ): BinaryResult {
        // Unique per execution: concurrent runs of the same command must not share an output file.
        val tmpStdOut = createTempFile(workTmpDir, ".$cmdUname", ".out").toFile()
        val process =
            ProcessBuilder(*cmdArray)
                .directory(workTmpDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(tmpStdOut)
                .start()
        if (!awaitCompletion(process)) {
            reapTimedOutProcess(process)
            tmpStdOut.appendText("timed out after $executionTimeoutMillis ms")
            return BinaryResult(code = -1, output = tmpStdOut)
        }
        return BinaryResult(code = process.exitValue(), output = tmpStdOut)
    }

    // Waits up to the execution backstop; the spawned process must not outlive an interrupted wait.
    private fun awaitCompletion(process: Process): Boolean =
        try {
            process.waitFor(executionTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupt: InterruptedException) {
            reapTimedOutProcess(process)
            Thread.currentThread().interrupt()
            throw interrupt
        }

    // Terminates gracefully first, then forcibly, and reaps before returning.
    private fun reapTimedOutProcess(process: Process) {
        process.destroy()
        if (process.waitFor(terminationGraceMillis, TimeUnit.MILLISECONDS)) return
        process.destroyForcibly().waitFor()
    }
}
