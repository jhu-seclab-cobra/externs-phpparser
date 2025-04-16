package edu.jhu.cobra.externs.phpparser.abc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import java.nio.file.Paths
import java.time.Duration

class AbcBinaryTest {
    /**
     * A test implementation of [AbcBinary] for unit testing.
     * This class provides a simple implementation that returns a fixed command array.
     */
    class TestAbcBinary : AbcBinary() {
        override fun getCommandArray(): Array<String> = arrayOf("test", "command")
    } 
    
    @Test
    fun testAbcBinaryInitialization() {
        val binary = TestAbcBinary()
        assertNotNull(binary)
    }

    @Test
    fun testAbcBinaryConfiguration() {
        val binary = TestAbcBinary().apply {
            workTmpDir = Paths.get("/tmp")
            timeout = Duration.ofSeconds(5)
            doCacheOutput = true
        }

        assertEquals(Paths.get("/tmp"), binary.workTmpDir)
        assertEquals(Duration.ofSeconds(5), binary.timeout)
        assertEquals(true, binary.doCacheOutput)
    }

    @Test
    fun testGetCommandArray() {
        val binary = TestAbcBinary()
        val commandArray = binary.getCommandArray()
        assertEquals(2, commandArray.size)
        assertEquals("test", commandArray[0])
        assertEquals("command", commandArray[1])
    }
} 