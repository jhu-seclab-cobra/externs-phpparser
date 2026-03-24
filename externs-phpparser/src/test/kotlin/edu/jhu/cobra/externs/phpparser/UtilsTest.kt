package edu.jhu.cobra.externs.phpparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path

class UtilsTest {
    @Test
    fun testSearchBinInDirectory() {
        // Create a temporary directory with some test files
        val tempDir = Files.createTempDirectory("testSearchBin")
        val testFile1 = tempDir.resolve("test1.txt").toFile()
        val testFile2 = tempDir.resolve("test2.txt").toFile()
        testFile1.createNewFile()
        testFile2.createNewFile()

        // Test finding existing file
        val foundFile = searchBin(tempDir, "test1.txt")
        assertNotNull(foundFile)
        assertEquals(testFile1.absolutePath, foundFile.absolutePath)

        // Test finding non-existent file
        val notFoundFile = searchBin(tempDir, "nonexistent.txt")
        assertNull(notFoundFile)

        // Clean up
        testFile1.delete()
        testFile2.delete()
        tempDir.toFile().delete()
    }

    @Test
    fun testSearchBinInPath() {
        // This test is platform-dependent, so we'll just test the basic functionality
        val foundPhp = searchBin("php")
        // We can't assert the exact value since it depends on the system
        // but we can check that it's either null or a valid file
        foundPhp?.let { assertTrue(it.exists()) }
    }

    @Test
    fun testIsPhpVersionValid() {
        // Create a mock PHP binary that outputs a specific version
        val mockPhp = File.createTempFile("mock-php", ".sh")
        mockPhp.writeText("#!/bin/sh\necho 'PHP 7.4.10 (cli)'")
        mockPhp.setExecutable(true)

        // Test version comparison
        assertTrue(isPhpVersionValid(mockPhp, "7.4"))
        assertTrue(isPhpVersionValid(mockPhp, "7.4.10"))
        assertTrue(isPhpVersionValid(mockPhp, "7.4.9"))
        assertTrue(!isPhpVersionValid(mockPhp, "7.5"))
        assertTrue(!isPhpVersionValid(mockPhp, "8.0"))

        // Test invalid version format
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mockPhp, "invalid.version")
        }

        // Clean up
        mockPhp.delete()
    }

    @Test
    fun testExtractFileFromZip() {
        // Create a temporary ZIP file with some test content
        val tempZip = Files.createTempFile("test", ".zip")
        val tempOutput = Files.createTempFile("output", ".txt")
        
        // Create a ZIP file with a test entry
        ZipOutputStream(tempZip.toFile().outputStream()).use { zipOut ->
            val entry = ZipEntry("test.txt")
            zipOut.putNextEntry(entry)
            zipOut.write("test content".toByteArray())
            zipOut.closeEntry()
        }

        // Test extracting the file
        val inputStream = tempZip.toFile().inputStream()
        val success = extractFileFromZip(inputStream, tempOutput, Path("test.txt"))
        assertTrue(success)
        assertEquals("test content", tempOutput.toFile().readText())

        // Test extracting non-existent file
        val inputStream2 = tempZip.toFile().inputStream()
        val success2 = extractFileFromZip(inputStream2, tempOutput, Path("nonexistent.txt"))
        assertTrue(!success2)

        // Clean up
        tempZip.toFile().delete()
        tempOutput.toFile().delete()
    }

    @Test
    fun testCrc32Checksum() {
        // Create a temporary file with known content
        val tempFile = Files.createTempFile("test", ".txt")
        tempFile.toFile().writeText("test content")

        // Test CRC32 checksum calculation
        val checksum = tempFile.crc32ChecksumString
        assertNotNull(checksum)
        assertEquals(8, checksum.length) // CRC32 should be 8 hex characters

        // Test with non-existent file
        val nonExistentFile = Path("nonexistent.txt")
        val nonExistentChecksum = nonExistentFile.crc32ChecksumString
        assertNull(nonExistentChecksum)

        // Clean up
        tempFile.toFile().delete()
    }
} 