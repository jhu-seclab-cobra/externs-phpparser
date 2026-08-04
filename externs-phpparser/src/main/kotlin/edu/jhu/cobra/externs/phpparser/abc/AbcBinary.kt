package edu.jhu.cobra.externs.phpparser.abc

import edu.jhu.cobra.externs.phpparser.ExternalBinaryArgumentMissException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.notExists
import kotlin.io.path.writeText
import kotlin.math.absoluteValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Abstract executable that runs in a working directory. */
@Suppress("UNCHECKED_CAST")
abstract class AbcBinary {
    private val tmpDir = Path(System.getProperty("java.io.tmpdir"))
    var workTmpDir = tmpDir / "cobra" / "binaries" / this::class.java.simpleName
    val allArguments: MutableMap<String, Any?> = mutableMapOf()
    val allOptions: MutableMap<String, Any> = mutableMapOf()
    var timeout: Duration = Duration.ofMinutes(1)
    var doCacheOutput: Boolean = false

    // Delegated property backed by allArguments.
    protected inner class Argument<T : Any?>(
        private val name: String,
        default: T? = null,
    ) : ReadWriteProperty<Any, T> {
        init {
            allArguments[name] = default
        }

        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): T = (allArguments[name] ?: throw ExternalBinaryArgumentMissException(name)) as T

        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T,
        ) {
            value?.let { allArguments[name] = it }
        }
    }

    // Delegated property backed by allOptions.
    protected inner class Option<T : Any?>(
        private val name: String,
        default: T? = null,
    ) : ReadWriteProperty<Any, T> {
        init {
            default?.let { allOptions[name] = default }
        }

        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): T = allOptions[name] as T

        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T,
        ) {
            value?.let { allOptions[name] = it }
        }
    }

    /** Builds the CLI command with all configured arguments and options. */
    abstract fun getCommandArray(): Array<String>

    /**
     * Runs the configured command and returns the result.
     * @return exit code and output file.
     */
    open fun execute(): BinaryResult {
        if (workTmpDir.notExists()) workTmpDir.createDirectories()
        val cmdArray = this.getCommandArray()
        val cmdUname = cmdArray.contentHashCode().absoluteValue.toString()
        val cacheFile = workTmpDir.resolve(".$cmdUname.cache")
        if (doCacheOutput && cacheFile.exists()) return BinaryResult(code = 0, output = cacheFile.toFile())
        val tmpStdOut = workTmpDir.resolve(".$cmdUname.out").toFile()
        val pBuilder =
            ProcessBuilder(*cmdArray)
                .directory(workTmpDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(tmpStdOut)
        val process = pBuilder.start()
        val isFinished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!isFinished) {
            process.destroy()
            val tmpFile = createTempFile().apply { writeText("timed out after $timeout") }
            return BinaryResult(code = -1, output = tmpFile.toFile())
        }
        val exitCode = process.exitValue()
        if (doCacheOutput && exitCode == 0) {
            tmpStdOut.toPath().moveTo(cacheFile, overwrite = true)
            return BinaryResult(code = 0, output = cacheFile.toFile())
        }
        return BinaryResult(code = exitCode, output = tmpStdOut)
    }
}
