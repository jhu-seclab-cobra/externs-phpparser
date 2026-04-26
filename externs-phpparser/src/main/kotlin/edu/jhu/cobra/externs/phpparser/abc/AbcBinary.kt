package edu.jhu.cobra.externs.phpparser.abc

import edu.jhu.cobra.externs.phpparser.ExternalBinaryArgumentMissException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
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
    protected inner class Argument<T : Any?>(private val name: String, default: T? = null) : ReadWriteProperty<Any, T> {
        init {
            allArguments[name] = default
        }

        override fun getValue(thisRef: Any, property: KProperty<*>): T {
            return (allArguments[name] ?: throw ExternalBinaryArgumentMissException(name)) as T
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            value?.let { allArguments[name] = it }
        }
    }

    // Delegated property backed by allOptions.
    protected inner class Option<T : Any?>(private val name: String, default: T? = null) : ReadWriteProperty<Any, T> {
        init {
            default?.let { allOptions[name] = default }
        }

        override fun getValue(thisRef: Any, property: KProperty<*>): T = allOptions[name] as T
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
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
        val tmpStdOut = workTmpDir.resolve(".$cmdUname.cache").toFile()
        if (doCacheOutput && tmpStdOut.exists()) return BinaryResult(code = 0, output = tmpStdOut)
        val pBuilder = ProcessBuilder(*cmdArray)
            .directory(workTmpDir.toFile())
            .redirectOutput(tmpStdOut)
            .redirectError(tmpStdOut)
            .redirectErrorStream(true)
        val process = pBuilder.start()
        val isFinished = process.waitFor(timeout.toMinutes(), TimeUnit.MINUTES)
        if (isFinished) return BinaryResult(code = process.exitValue(), output = tmpStdOut)
        val tmpFile = createTempFile().apply { writeText("timed out after $timeout minutes") }
        return BinaryResult(code = -1, output = tmpFile.toFile()).also { process.destroy() }

    }
}