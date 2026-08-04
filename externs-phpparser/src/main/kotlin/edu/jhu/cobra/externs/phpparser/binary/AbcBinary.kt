package edu.jhu.cobra.externs.phpparser.binary

import edu.jhu.cobra.externs.phpparser.ExternalBinaryArgumentMissException
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.notExists
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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

/** Abstract executable that runs in a working directory. */
public abstract class AbcBinary {
    private val tmpDir = Path(System.getProperty("java.io.tmpdir"))
    public var workTmpDir: Path = tmpDir / "cobra" / "binaries" / this::class.java.simpleName
    internal val allArguments: MutableMap<String, Any?> = mutableMapOf()
    internal val allOptions: MutableMap<String, Any> = mutableMapOf()
    public var doCacheOutput: Boolean = false

    // Liveness backstops. Open so test doubles can shorten waits; never per-run configuration.
    internal open val executionTimeoutMillis: Long = EXECUTION_TIMEOUT_MILLIS
    internal open val terminationGraceMillis: Long = TERMINATION_GRACE_MILLIS

    // Delegated property backed by allArguments.
    protected inner class Argument<T : Any?>(
        private val name: String,
        default: T? = null,
    ) : ReadWriteProperty<Any, T> {
        init {
            allArguments[name] = default
        }

        @Suppress("UNCHECKED_CAST")
        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): T = (allArguments[name] ?: throw ExternalBinaryArgumentMissException(name)) as T

        /** Assigning null removes the argument; a later read throws [ExternalBinaryArgumentMissException]. */
        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T,
        ) {
            if (value == null) allArguments.remove(name) else allArguments[name] = value
        }
    }

    // Delegated property backed by allOptions.
    protected inner class Option<T : Any?>(
        private val name: String,
        private val default: T? = null,
    ) : ReadWriteProperty<Any, T> {
        init {
            default?.let { allOptions[name] = it }
        }

        @Suppress("UNCHECKED_CAST")
        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): T {
            val value = allOptions[name]
            if (value != null) return value as T
            // A null default marks a nullable option: absence reads back as null.
            if (default == null) return null as T
            error("Option $name has no value: it was removed after being declared with default $default")
        }

        /** Assigning null removes the option, excluding it from the command line. */
        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T,
        ) {
            if (value == null) allOptions.remove(name) else allOptions[name] = value
        }
    }

    /** Builds the CLI command with all configured arguments and options. */
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
        val tmpStdOut = workTmpDir.resolve(".$cmdUname.out").toFile()
        val pBuilder =
            ProcessBuilder(*cmdArray)
                .directory(workTmpDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(tmpStdOut)
        val process = pBuilder.start()
        val isFinished = process.waitFor(executionTimeoutMillis, TimeUnit.MILLISECONDS)
        if (!isFinished) {
            reapTimedOutProcess(process)
            tmpStdOut.appendText("timed out after $executionTimeoutMillis ms")
            return BinaryResult(code = -1, output = tmpStdOut)
        }
        val exitCode = process.exitValue()
        if (doCacheOutput && exitCode == 0) {
            tmpStdOut.toPath().moveTo(cacheFile, overwrite = true)
            return BinaryResult(code = 0, output = cacheFile.toFile())
        }
        return BinaryResult(code = exitCode, output = tmpStdOut)
    }

    /**
     * Runs [block] and restores the argument and option registries afterward, on both
     * normal completion and exception.
     */
    internal fun <R> withConfigurationSnapshot(block: () -> R): R {
        val argumentsSnapshot = HashMap(allArguments)
        val optionsSnapshot = HashMap(allOptions)
        try {
            return block()
        } finally {
            allArguments.clear()
            allArguments.putAll(argumentsSnapshot)
            allOptions.clear()
            allOptions.putAll(optionsSnapshot)
        }
    }

    // Terminates gracefully first, then forcibly, and reaps before returning.
    private fun reapTimedOutProcess(process: Process) {
        process.destroy()
        if (process.waitFor(terminationGraceMillis, TimeUnit.MILLISECONDS)) return
        process.destroyForcibly().waitFor()
    }
}
