package edu.jhu.cobra.externs.phpparser.abc

/**
 * Tests for [AbcBinary] — abstract binary execution framework.
 *
 * - `should initialize with default timeout and cache settings` — verifies default timeout (1 min) and cache (off).
 * - `should throw when reading unset argument` — Argument delegate throws on null read.
 * - `should set and get argument via delegate` — Argument round-trip through delegate.
 * - `should set and get option via delegate` — Option round-trip and default value.
 * - `should build command array with options and arguments` — getCommandArray includes options and args.
 * - `should execute and return success result` — execute returns code 0 with output file.
 * - `should return cached output on repeated execution` — second execute returns same cached file.
 * - `should keep distinct cache entries for hash-colliding commands` — 32-bit contentHashCode collision
 *   must not replay the wrong cached output.
 * - `should not replay failed run from cache` — a failed run is never cached as success.
 * - `should return code -1 on timeout` — timed-out process returns code -1.
 * - `should reap TERM-ignoring process before returning on timeout` — destroy escalates to destroyForcibly
 *   so no process survives past the timeout return.
 * - `timeout output should render duration in milliseconds` — no ISO-8601 duration in the timeout message.
 * - `timeout output should stay inside workTmpDir` — no orphan temp file outside the working directory.
 * - `should honor sub-minute timeout` — a sub-minute timeout waits instead of truncating to zero.
 * - `should restore config after executeWith` — arguments restored after executeWith.
 * - `should use temporary config during executeWith` — temporary config used during execution.
 * - `should return null for option with null default` — Option with no default returns null.
 * - `should read string option value` — string Option read/write.
 * - `should skip creating workTmpDir if it already exists` — no error on existing dir.
 * - `should remove option when setting null` — null setValue removes the key; read returns null.
 * - `should ignore null when setting argument via allArguments` — null in map causes throw on read.
 * - `should remove argument when setting null` — null setValue removes the key; read throws.
 * - `should throw when option with non-null default is missing` — missing key names the option.
 * - `executeWith restores state when execute throws` — try-finally restores state on exception.
 */

import edu.jhu.cobra.externs.phpparser.ExternalBinaryArgumentMissException
import edu.jhu.cobra.externs.phpparser.executeWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class AbcBinaryTest {
    @TempDir
    lateinit var tempDir: Path

    class EchoBinary : AbcBinary() {
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

    class SleepBinary(
        private val seconds: Int = 60,
    ) : AbcBinary() {
        override fun getCommandArray(): Array<String> = arrayOf("sleep", seconds.toString())
    }

    class FailBinary : AbcBinary() {
        override fun getCommandArray(): Array<String> = arrayOf("false")
    }

    // Ignores SIGTERM; only SIGKILL ends it. The marker makes the process findable via pgrep.
    class StubbornBinary(
        marker: String,
    ) : AbcBinary() {
        private val script = "trap '' TERM; while :; do sleep 1; done # $marker"

        override fun getCommandArray(): Array<String> = arrayOf("sh", "-c", script)
    }

    @Test
    fun `should initialize with default timeout and cache settings`() {
        val binary = EchoBinary()
        assertEquals(Duration.ofMinutes(1), binary.timeout)
        assertEquals(false, binary.doCacheOutput)
    }

    @Test
    fun `should throw when reading unset argument`() {
        val binary = EchoBinary()
        assertFailsWith<ExternalBinaryArgumentMissException> {
            @Suppress("UNUSED_VARIABLE")
            val msg = binary.message
        }
    }

    @Test
    fun `should set and get argument via delegate`() {
        val binary = EchoBinary()
        binary.message = "hello"
        assertEquals("hello", binary.message)
    }

    @Test
    fun `should set and get option via delegate`() {
        val binary = EchoBinary()
        binary.verbose = true
        assertEquals(true, binary.verbose)
        assertEquals("text", binary.outputFormat)
    }

    @Test
    fun `should build command array with options and arguments`() {
        val binary = EchoBinary()
        binary.message = "hello"
        binary.verbose = true
        val cmd = binary.getCommandArray()
        assertTrue(cmd.contains("echo"))
        assertTrue(cmd.contains("--verbose"))
        assertTrue(cmd.contains("hello"))
    }

    @Test
    fun `should execute and return success result`() {
        val binary = EchoBinary()
        binary.message = "hello"
        val result = binary.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.exists())
        assertTrue(result.output.readText().contains("hello"))
    }

    @Test
    fun `should return cached output on repeated execution`() {
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.message = "cache-test"
        binary.doCacheOutput = true

        val result1 = binary.execute()
        assertEquals(0, result1.code)

        val result2 = binary.execute()
        assertEquals(0, result2.code)
        assertEquals(result1.output.absolutePath, result2.output.absolutePath)
    }

    @Test
    fun `should keep distinct cache entries for hash-colliding commands`() {
        // "Aa" and "BB" share a String.hashCode, so the command arrays collide on contentHashCode.
        assertEquals(arrayOf("echo", "Aa").contentHashCode(), arrayOf("echo", "BB").contentHashCode())
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.doCacheOutput = true

        binary.message = "Aa"
        val result1 = binary.execute()
        assertEquals(0, result1.code)

        binary.message = "BB"
        val result2 = binary.execute()
        assertEquals(0, result2.code)
        assertNotEquals(result1.output.absolutePath, result2.output.absolutePath)
        assertTrue(
            result2.output.readText().contains("BB"),
            "colliding command must not replay the other command's cached output",
        )
    }

    @Test
    fun `should not replay failed run from cache`() {
        val binary = FailBinary()
        binary.workTmpDir = tempDir
        binary.doCacheOutput = true

        val result1 = binary.execute()
        assertTrue(result1.code != 0)

        val result2 = binary.execute()
        assertTrue(result2.code != 0)
    }

    @Test
    fun `should return code -1 on timeout`() {
        val binary = SleepBinary()
        binary.workTmpDir = tempDir
        binary.timeout = Duration.ofSeconds(0)

        val result = binary.execute()
        assertEquals(-1, result.code)
        assertTrue(result.output.exists())
    }

    @Test
    fun `should reap TERM-ignoring process before returning on timeout`() {
        val marker = "cobra-abc-reap-${UUID.randomUUID()}"
        val binary = StubbornBinary(marker)
        binary.workTmpDir = tempDir
        binary.timeout = Duration.ofMillis(200)

        val result = binary.execute()
        val survivor = ProcessBuilder("pgrep", "-f", marker).start()
        survivor.waitFor()
        val stillAlive = survivor.exitValue() == 0
        // Reap any survivor so a failure does not leak a 60s process into the environment.
        ProcessBuilder("pkill", "-9", "-f", marker).start().waitFor()
        assertEquals(-1, result.code)
        assertFalse(stillAlive, "process ignoring SIGTERM must be force-killed before execute returns")
    }

    @Test
    fun `timeout output should render duration in milliseconds`() {
        val binary = SleepBinary()
        binary.workTmpDir = tempDir
        binary.timeout = Duration.ofSeconds(0)

        val result = binary.execute()
        val message = result.output.readText()
        assertTrue("timed out after 0 ms" in message, "message should render milliseconds, got: $message")
    }

    @Test
    fun `timeout output should stay inside workTmpDir`() {
        val binary = SleepBinary()
        binary.workTmpDir = tempDir
        binary.timeout = Duration.ofSeconds(0)

        val result = binary.execute()
        assertEquals(
            binary.workTmpDir.toFile().absolutePath,
            result.output.parentFile.absolutePath,
            "timeout output must not leave an orphan file outside workTmpDir",
        )
    }

    @Test
    fun `should honor sub-minute timeout`() {
        val binary = SleepBinary(seconds = 1)
        binary.workTmpDir = tempDir
        binary.timeout = Duration.ofSeconds(30)

        val result = binary.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `should restore config after executeWith`() {
        val binary = EchoBinary()
        binary.message = "original"

        val result =
            binary.executeWith {
                message = "temporary"
            }
        assertNotNull(result)
        assertEquals("original", binary.message)
    }

    @Test
    fun `should use temporary config during executeWith`() {
        val binary = EchoBinary()
        binary.message = "original"
        binary.verbose = false

        val result =
            binary.executeWith {
                message = "temp-msg"
                verbose = true
            }
        assertEquals(0, result.code)
        assertTrue(result.output.readText().contains("temp-msg"))
        assertEquals(false, binary.verbose)
    }

    @Test
    fun `should return null for option with null default`() {
        val binary = EchoBinary()
        val value = binary.nullableOpt
        assertEquals(null, value)
    }

    @Test
    fun `should read string option value`() {
        val binary = EchoBinary()
        assertEquals("text", binary.outputFormat)
        binary.outputFormat = "json"
        assertEquals("json", binary.outputFormat)
    }

    @Test
    fun `should skip creating workTmpDir if it already exists`() {
        val binary = EchoBinary()
        binary.message = "dir-exists"
        val r1 = binary.execute()
        assertEquals(0, r1.code)
        val r2 = binary.execute()
        assertEquals(0, r2.code)
    }

    @Test
    fun `should remove option when setting null`() {
        val binary = EchoBinary()
        binary.nullableOpt = "value"
        assertEquals("value", binary.nullableOpt)
        binary.nullableOpt = null
        assertEquals(null, binary.nullableOpt)
        assertFalse("--nullable" in binary.allOptions, "null assignment should remove the option key")
    }

    @Test
    fun `should throw when option with non-null default is missing`() {
        val binary = EchoBinary()
        binary.allOptions.remove("--format")
        val exception =
            assertFailsWith<IllegalStateException> {
                @Suppress("UNUSED_VARIABLE")
                val format = binary.outputFormat
            }
        assertTrue("--format" in exception.message.orEmpty(), "message should name the option: ${exception.message}")
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `should ignore null when setting argument via allArguments`() {
        val binary = EchoBinary()
        binary.message = "first"
        assertEquals("first", binary.message)
        binary.allArguments["message"] = null
        assertFailsWith<ExternalBinaryArgumentMissException> {
            @Suppress("UNUSED_VARIABLE")
            val m = binary.message
        }
    }

    @Test
    fun `should remove argument when setting null`() {
        val binary = EchoBinary()
        binary.nullableArg = "value"
        assertEquals("value", binary.nullableArg)
        binary.nullableArg = null
        assertFalse("nullableArg" in binary.allArguments, "null assignment should remove the argument key")
        assertFailsWith<ExternalBinaryArgumentMissException> {
            @Suppress("UNUSED_VARIABLE")
            val arg = binary.nullableArg
        }
    }

    @Test
    fun `executeWith restores state when execute throws`() {
        val binary =
            object : AbcBinary() {
                var arg: String by Argument<String>("arg")
                var opt: Boolean by Option("--flag", false)

                override fun getCommandArray(): Array<String> = arrayOf("echo")

                override fun execute(): BinaryResult = error("boom")
            }
        binary.arg = "original"
        binary.opt = false

        assertFailsWith<IllegalStateException> {
            binary.executeWith {
                arg = "temporary"
                opt = true
            }
        }
        assertEquals("original", binary.arg)
        assertEquals(false, binary.opt)
    }
}
