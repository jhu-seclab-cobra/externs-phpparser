package edu.jhu.cobra.externs.phpparser

/**
 * Tests for exception types — message formatting and constructor behavior.
 *
 * - `ExternalBinaryNotFoundException should include directory in message` — message includes directory path.
 * - `ExternalBinaryNotFoundException should default to system when no directory` — message says "the system".
 * - `ExternalBinaryInvalidException should include reason in message` — message includes reason string.
 * - `ExternalBinaryInvalidException should handle null reason` — message shows "null" for omitted reason.
 * - `ExternalBinaryInvalidException should carry cause` — constructor cause is preserved.
 * - `ExternalBinaryArgumentMissException should include argument name` — message includes argument name, no trailing space.
 * - `ExternalBinaryArgumentMissException should be a RuntimeException` — unchecked like its siblings.
 */

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExceptionsTest {
    @Test
    fun `ExternalBinaryNotFoundException should include directory in message`() {
        val ex = ExternalBinaryNotFoundException("php", "/usr/local/bin")
        assertEquals("php does not exist under /usr/local/bin.", ex.message)
    }

    @Test
    fun `ExternalBinaryNotFoundException should default to system when no directory`() {
        val ex = ExternalBinaryNotFoundException("php")
        assertEquals("php does not exist under the system.", ex.message)
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
    fun `ExternalBinaryInvalidException should carry cause`() {
        val cause = IOException("cannot run")
        val ex = ExternalBinaryInvalidException("php", "probe failed", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `ExternalBinaryArgumentMissException should include argument name`() {
        val ex = ExternalBinaryArgumentMissException("target")
        assertEquals("Argument target has not been initialized.", ex.message)
    }

    @Test
    fun `ExternalBinaryArgumentMissException should be a RuntimeException`() {
        val ex: Exception = ExternalBinaryArgumentMissException("target")
        assertTrue(ex is RuntimeException, "expected unchecked exception, got: ${ex::class}")
    }
}
