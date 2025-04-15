package cobra.extern.phpparser.abc

import cobra.extern.phpparser.ExternalBinaryArgumentMissException
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.math.absoluteValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Represents an abstract executable task that can be configured and executed in a specified working directory.
 * This class provides a structured way to define and manage arguments and options for external program execution.
 *
 * @property allArguments A map of all arguments for the executable.
 * @property allOptions A map of all options for the executable.
 * @property workTmpDir The working directory where the executable is run. Defaults to the current directory.
 * @property timeout The maximum time allowed for the execution. Defaults to one minute.
 * @property doCacheOutput Indicates whether the output should be cached, to avoid repeated executions.
 */
@Suppress("UNCHECKED_CAST")
abstract class AbcBinary {

    private val tmpDir = Path(System.getProperty("java.io.tmpdir"))
    var workTmpDir = tmpDir / "cobra" / "binaries" / this::class.java.simpleName
    val allArguments: MutableMap<String, Any?> = mutableMapOf()
    val allOptions: MutableMap<String, Any> = mutableMapOf()
    var timeout: Duration = Duration.ofMinutes(1)
    var doCacheOutput: Boolean = false

    /**
     * Provides a delegated property for managing an argument of the executable.
     *
     * @param T The type of the argument value.
     * @property name The name of the argument.
     * @param default The default value of the argument if not explicitly set.
     */
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

    /**
     * Provides a delegated property for managing an option of the executable.
     *
     * @param T The type of the option value.
     * @property name The name of the option.
     * @param default The default value of the option if not explicitly set.
     */
    protected inner class Option<T : Any?>(private val name: String, default: T? = null) : ReadWriteProperty<Any, T> {
        init {
            default?.let { allOptions[name] = default }
        }

        override fun getValue(thisRef: Any, property: KProperty<*>): T = allOptions[name] as T
        override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
            value?.let { allOptions[name] = it }
        }
    }

    /**
     * Abstract function that constructs the command array for execution.
     * Implementations must provide the specific command and arguments as an array of strings.
     *
     * @return Array of strings that represents the command and its arguments.
     */
    abstract fun getCommandArray(): Array<String>

    /**
     * Executes the command constructed by `getCommandArray` and handles output and error redirection.
     * Manages execution timeout and output caching based on instance configuration.
     *
     * @return An ExecuteResult containing the exit code and a reference to the output file.
     * @throws ExecutableMissException If the executable command is not found.
     */
    open fun execute(): BinaryResult {
        if (workTmpDir.notExists()) workTmpDir.createDirectories()
        val cmdArray = this.getCommandArray()
        val cmdUname = cmdArray.joinToString(" ").hashCode().absoluteValue.toString()
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