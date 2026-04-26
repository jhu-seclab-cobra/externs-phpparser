package edu.jhu.cobra.externs.phpparser

/**
 * Tests for exception types — message formatting and constructor behavior.
 *
 * - `ExternalBinaryNotFoundException should include directory in message` — message includes directory path.
 * - `ExternalBinaryNotFoundException should default to system when no directory` — message says "the system".
 * - `ExternalBinaryInvalidException should include reason in message` — message includes reason string.
 * - `ExternalBinaryInvalidException should handle null reason` — message shows "null" for omitted reason.
 * - `ExternalBinaryArgumentMissException should include argument name` — message includes argument name.
 */

import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionsTest {

    @Test
    fun `ExternalBinaryNotFoundException should include directory in message`() {
        val ex = ExternalBinaryNotFoundException("php", "/usr/local/bin")
        assertEquals("php do not exist under /usr/local/bin.", ex.message)
    }

    @Test
    fun `ExternalBinaryNotFoundException should default to system when no directory`() {
        val ex = ExternalBinaryNotFoundException("php")
        assertEquals("php do not exist under the system.", ex.message)
    }

    @Test
    fun `ExternalBinaryInvalidException should include reason in message`() {
        val ex = ExternalBinaryInvalidException("php", "version mismatch")
        assertEquals("php provided is invalid: version mismatch", ex.message)
    }

    @Test
    fun `ExternalBinaryInvalidException should handle null reason`() {
        val ex = ExternalBinaryInvalidException("php")
        assertEquals("php provided is invalid: null", ex.message)
    }

    @Test
    fun `ExternalBinaryArgumentMissException should include argument name`() {
        val ex = ExternalBinaryArgumentMissException("target")
        assertEquals("Argument target has not been initialized. ", ex.message)
    }
}
