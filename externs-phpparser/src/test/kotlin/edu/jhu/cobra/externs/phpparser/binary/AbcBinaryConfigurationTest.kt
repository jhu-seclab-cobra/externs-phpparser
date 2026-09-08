package edu.jhu.cobra.externs.phpparser.binary

/*
 * Tests for [AbcBinary] configuration — Argument/Option delegates and command assembly.
 *
 * - `should initialize with default backstops and cache settings` — verifies backstop constants and cache (off).
 * - `should throw when reading unset argument` — Argument delegate throws on null read.
 * - `should set and get argument via delegate` — Argument round-trip through delegate.
 * - `should set and get option via delegate` — Option round-trip and default value.
 * - `should build command array with options and arguments` — getCommandArray includes options and args.
 * - `should return null for option with null default` — Option with no default returns null.
 * - `should read string option value` — string Option read/write.
 * - `should remove option when setting null` — null setValue removes the key; read returns null.
 * - `should throw when option with non-null default is missing` — missing key names the option.
 * - `should ignore null when setting argument via allArguments` — null in map causes throw on read.
 * - `should remove argument when setting null` — null setValue removes the key; read throws.
 */

import edu.jhu.cobra.externs.phpparser.ExternalBinaryArgumentMissException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AbcBinaryConfigurationTest {
    @Test
    fun `should initialize with default backstops and cache settings`() {
        val binary = EchoBinary()
        assertEquals(EXECUTION_TIMEOUT_MILLIS, binary.executionTimeoutMillis)
        assertEquals(TERMINATION_GRACE_MILLIS, binary.terminationGraceMillis)
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
}
