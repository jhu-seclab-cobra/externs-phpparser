package edu.jhu.cobra.externs.phpparser

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
        assertEquals("php provided is valid, because version mismatch.", ex.message)
    }

    @Test
    fun `ExternalBinaryInvalidException should handle null reason`() {
        val ex = ExternalBinaryInvalidException("php")
        assertEquals("php provided is valid, because null.", ex.message)
    }

    @Test
    fun `ExternalBinaryArgumentMissException should include argument name`() {
        val ex = ExternalBinaryArgumentMissException("target")
        assertEquals("Argument target has not been initialized. ", ex.message)
    }
}
