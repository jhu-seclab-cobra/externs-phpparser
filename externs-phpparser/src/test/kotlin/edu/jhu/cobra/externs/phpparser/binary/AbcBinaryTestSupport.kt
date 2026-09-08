package edu.jhu.cobra.externs.phpparser.binary

/*
 * Shared fixture binaries for [AbcBinary] tests.
 *
 * - [EchoBinary] — echoes a message; exercises Argument/Option delegates and command assembly.
 * - [SleepBinary] — sleeps; tests override the execution backstop to observe timeouts quickly.
 * - [FailBinary] — exits non-zero; exercises failure paths.
 * - [StubbornBinary] — ignores SIGTERM; exercises forcible reaping with shortened backstops.
 * - [MissingBinary] — command names a nonexistent executable; exercises the spawn-failure path.
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

// Ignores SIGTERM; only SIGKILL ends it. The marker makes the process findable via pgrep.
internal class StubbornBinary(
    marker: String,
    override val executionTimeoutMillis: Long,
    override val terminationGraceMillis: Long,
) : AbcBinary() {
    private val script = "trap '' TERM; while :; do sleep 1; done # $marker"

    override fun getCommandArray(): Array<String> = arrayOf("sh", "-c", script)
}
