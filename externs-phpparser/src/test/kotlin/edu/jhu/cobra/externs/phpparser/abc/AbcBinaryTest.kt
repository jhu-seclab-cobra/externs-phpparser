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
 * - `should return code -1 on timeout` — timed-out process returns code -1.
 * - `should restore config after executeWith` — arguments restored after executeWith.
 * - `should use temporary config during executeWith` — temporary config used during execution.
 * - `should return null for option with null default` — Option with no default returns null.
 * - `should read string option value` — string Option read/write.
 * - `should skip creating workTmpDir if it already exists` — no error on existing dir.
 * - `should ignore null when setting nullable option` — null setValue is a no-op.
 * - `should ignore null when setting argument via allArguments` — null in map causes throw on read.
 * - `should ignore null when setting nullable argument` — null setValue is a no-op for Argument.
 * - `executeWith restores state when execute throws` — try-finally restores state on exception.
 */

import edu.jhu.cobra.externs.phpparser.ExternalBinaryArgumentMissException
import edu.jhu.cobra.externs.phpparser.executeWith
import java.io.File
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AbcBinaryTest {

    class EchoBinary : AbcBinary() {
        var message: String by Argument<String>("message")
        var nullableArg: String? by Argument<String?>("nullableArg")
        var verbose: Boolean by Option("--verbose", false)
        var outputFormat: String by Option("--format", "text")
        var nullableOpt: String? by Option<String?>("--nullable")

        override fun getCommandArray(): Array<String> = buildList {
            add("echo")
            for ((key, value) in allOptions) if (value is Boolean && value) add(key)
            add(message)
        }.toTypedArray()
    }

    class SleepBinary : AbcBinary() {
        override fun getCommandArray(): Array<String> = arrayOf("sleep", "60")
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
        binary.message = "cache-test"
        binary.doCacheOutput = true

        val result1 = binary.execute()
        assertEquals(0, result1.code)

        val result2 = binary.execute()
        assertEquals(0, result2.code)
        assertEquals(result1.output.absolutePath, result2.output.absolutePath)
    }

    @Test
    fun `should return code -1 on timeout`() {
        val binary = SleepBinary()
        binary.timeout = Duration.ofSeconds(0)

        val result = binary.execute()
        assertEquals(-1, result.code)
        assertTrue(result.output.exists())
    }

    @Test
    fun `should restore config after executeWith`() {
        val binary = EchoBinary()
        binary.message = "original"

        val result = binary.executeWith {
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

        val result = binary.executeWith {
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
    fun `should ignore null when setting nullable option`() {
        val binary = EchoBinary()
        binary.nullableOpt = "value"
        assertEquals("value", binary.nullableOpt)
        binary.nullableOpt = null
        assertEquals("value", binary.nullableOpt)
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
    fun `should ignore null when setting nullable argument`() {
        val binary = EchoBinary()
        binary.nullableArg = "value"
        assertEquals("value", binary.nullableArg)
        binary.nullableArg = null
        assertEquals("value", binary.nullableArg)
    }

    @Test
    fun `executeWith restores state when execute throws`() {
        val binary = object : AbcBinary() {
            var arg: String by Argument<String>("arg")
            var opt: Boolean by Option("--flag", false)
            override fun getCommandArray(): Array<String> = arrayOf("echo")
            override fun execute(): BinaryResult = throw RuntimeException("boom")
        }
        binary.arg = "original"
        binary.opt = false

        assertFailsWith<RuntimeException> {
            binary.executeWith {
                arg = "temporary"
                opt = true
            }
        }
        assertEquals("original", binary.arg)
        assertEquals(false, binary.opt)
    }
}
