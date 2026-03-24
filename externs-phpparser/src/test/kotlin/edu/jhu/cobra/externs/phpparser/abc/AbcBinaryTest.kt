package edu.jhu.cobra.externs.phpparser.abc

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
}
