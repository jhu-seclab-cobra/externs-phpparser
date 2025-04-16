package edu.jhu.cobra.externs.phpparser.abc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.io.File

class BinaryResultTest {
    @Test
    fun testSuccessfulExecution() {
        val outputFile = File("/tmp/output.txt")
        val result = BinaryResult(0, outputFile)
        assertNotNull(result)
        assertEquals(0, result.code)
        assertEquals(outputFile, result.output)
    }

    @Test
    fun testErrorExecution() {
        val outputFile = File("/tmp/error.txt")
        val result = BinaryResult(1, outputFile)
        assertNotNull(result)
        assertEquals(1, result.code)
        assertEquals(outputFile, result.output)
    }
} 