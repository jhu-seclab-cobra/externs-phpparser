package cobra.extern.phpparser.abc

import java.io.File

/**
 * Represents the result of an executable task, encapsulating the exit code and the output file where the execution results are stored.
 *
 * @property code The exit code returned by the executable. Typically, a code of 0 indicates successful execution.
 * @property output A [File] object pointing to the file where the standard output and standard error of the executable are redirected.
 */
data class BinaryResult(val code: Int, val output: File)


