package edu.jhu.cobra.externs.phpparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExceptionsTest {
    @Test
    fun testExternalBinaryNotFoundExceptionWithDirectory() {
        val exception = ExternalBinaryNotFoundException("php", "/usr/local/bin")
        assertNotNull(exception)
        assertEquals("php do not exist under /usr/local/bin.", exception.message)
    }

    @Test
    fun testExternalBinaryNotFoundExceptionWithoutDirectory() {
        val exception = ExternalBinaryNotFoundException("php")
        assertNotNull(exception)
        assertEquals("php do not exist under the system.", exception.message)
    }

    @Test
    fun testExternalBinaryInvalidException() {
        val exception = ExternalBinaryInvalidException("php", "version mismatch")
        assertNotNull(exception)
        assertEquals("php provided is valid, because version mismatch.", exception.message)
    }

    @Test
    fun testExternalBinaryArgumentMissException() {
        val exception = ExternalBinaryArgumentMissException("target")
        assertNotNull(exception)
        assertEquals("Argument target has not been initialized. ", exception.message)
    }
} 