package edu.jhu.cobra.externs.phpparser

/**
 * Tests for utility functions in Utils.kt — binary search, version validation, ZIP extraction, CRC32.
 *
 * - `searchBin under directory should find existing file` — finds file at root of search directory.
 * - `searchBin under directory should find file in subdirectory` — finds file in nested subdirectory.
 * - `searchBin under directory should return null for missing file` — returns null when no match.
 * - `searchBin by name should find php on PATH or return null` — system PATH search for php.
 * - `searchBin by name should return null for nonexistent binary` — returns null for unknown binary.
 * - `isPhpVersionValid should return true when current version is higher` — higher version passes.
 * - `isPhpVersionValid should return true when versions are equal and includeEqual is true` — equal passes with flag.
 * - `isPhpVersionValid should return false when versions are equal and includeEqual is false` — equal fails without flag.
 * - `isPhpVersionValid should return false when current version is lower` — lower version fails.
 * - `isPhpVersionValid should handle incomplete version numbers` — 1- and 2-part versions compared correctly.
 * - `isPhpVersionValid should throw on invalid minRequired format` — invalid format throws.
 * - `isPhpVersionValid should throw when binary produces no version output` — non-version output throws.
 * - `isPhpVersionValid should throw when binary does not exist` — missing binary throws.
 * - `isPhpVersionValid should compare major version correctly` — major-only comparison.
 * - `isPhpVersionValid should compare minor version when major is equal` — minor comparison.
 * - `isPhpVersionValid should compare patch version when major and minor are equal` — patch comparison.
 * - `isPhpVersionValid should handle single-digit minRequired` — single-component version.
 * - `isPhpVersionValid should throw when binary outputs empty` — empty output throws.
 * - `isPhpVersionValid should handle two-part minRequired against three-part current` — mixed lengths.
 * - `extractFileFromZip should extract matching entry` — extracts target file from ZIP.
 * - `extractFileFromZip should return false when entry not found` — returns false for missing entry.
 * - `extractFileFromZip should match multiple possible paths` — matches any of multiple candidate paths.
 * - `extractFileFromZip should normalize backslash paths` — backslash-to-forward-slash normalization.
 * - `Path crc32ChecksumString should return 8-char hex for existing file` — valid checksum format.
 * - `Path crc32ChecksumString should return null for nonexistent file` — null for missing file.
 * - `Path crc32ChecksumString should return null for directory` — null for directory path.
 * - `Path crc32ChecksumString should be deterministic` — same file produces same checksum.
 * - `File crc32ChecksumString should delegate to Path extension` — File extension matches Path extension.
 * - `File crc32ChecksumString should return null for nonexistent file` — null for missing file via File extension.
 */

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilsTest {

    // --- searchBin(under, possibleNames) ---

    @Test
    fun `searchBin under directory should find existing file`() {
        val tempDir = createTempDirectory("searchBin")
        val target = tempDir.resolve("mybin").toFile().apply { createNewFile() }
        val found = searchBin(tempDir, "mybin")
        assertNotNull(found)
        assertEquals(target.absolutePath, found.absolutePath)
        target.delete()
        tempDir.toFile().delete()
    }

    @Test
    fun `searchBin under directory should find file in subdirectory`() {
        val tempDir = createTempDirectory("searchBinSub")
        val subDir = (tempDir.resolve("sub")).apply { createDirectories() }
        val target = subDir.resolve("deep.bin").toFile().apply { createNewFile() }
        val found = searchBin(tempDir, "deep.bin")
        assertNotNull(found)
        assertEquals(target.absolutePath, found.absolutePath)
        target.delete()
        subDir.toFile().delete()
        tempDir.toFile().delete()
    }

    @Test
    fun `searchBin under directory should return null for missing file`() {
        val tempDir = createTempDirectory("searchBinMiss")
        assertNull(searchBin(tempDir, "nonexistent"))
        tempDir.toFile().delete()
    }

    // --- searchBin(name) on PATH ---

    @Test
    fun `searchBin by name should find php on PATH or return null`() {
        val result = searchBin("php")
        result?.let { assertTrue(it.exists()) }
    }

    @Test
    fun `searchBin by name should return null for nonexistent binary`() {
        assertNull(searchBin("totally-nonexistent-binary-xyz-999"))
    }

    // --- isPhpVersionValid ---

    @Test
    fun `isPhpVersionValid should return true when current version is higher`() {
        val mock = createMockPhpBinary("8.2.5")
        assertTrue(isPhpVersionValid(mock, "7.1"))
        assertTrue(isPhpVersionValid(mock, "8.2.4"))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should return true when versions are equal and includeEqual is true`() {
        val mock = createMockPhpBinary("7.4.10")
        assertTrue(isPhpVersionValid(mock, "7.4.10", includeEqual = true))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should return false when versions are equal and includeEqual is false`() {
        val mock = createMockPhpBinary("7.4.10")
        assertFalse(isPhpVersionValid(mock, "7.4.10", includeEqual = false))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should return false when current version is lower`() {
        val mock = createMockPhpBinary("7.4.10")
        assertFalse(isPhpVersionValid(mock, "8.0"))
        assertFalse(isPhpVersionValid(mock, "7.5"))
        assertFalse(isPhpVersionValid(mock, "7.4.11"))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should handle incomplete version numbers`() {
        val mock = createMockPhpBinary("8.1.0")
        assertTrue(isPhpVersionValid(mock, "8"))
        assertTrue(isPhpVersionValid(mock, "8.1"))
        assertFalse(isPhpVersionValid(mock, "8.2"))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should throw on invalid minRequired format`() {
        val mock = createMockPhpBinary("8.0.0")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "invalid.version")
        }
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should throw when binary produces no version output`() {
        val mock = File.createTempFile("mock-php-bad", ".sh").apply {
            writeText("#!/bin/sh\necho 'not a version'")
            setExecutable(true)
        }
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "7.1")
        }
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should throw when binary does not exist`() {
        val fake = File("/tmp/nonexistent-php-binary-xyz")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(fake, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should compare major version correctly`() {
        val mock = createMockPhpBinary("8.0.0")
        assertTrue(isPhpVersionValid(mock, "7.0.0"))
        assertFalse(isPhpVersionValid(mock, "9.0.0"))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should compare minor version when major is equal`() {
        val mock = createMockPhpBinary("8.2.0")
        assertTrue(isPhpVersionValid(mock, "8.1.0"))
        assertFalse(isPhpVersionValid(mock, "8.3.0"))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should compare patch version when major and minor are equal`() {
        val mock = createMockPhpBinary("8.2.5")
        assertTrue(isPhpVersionValid(mock, "8.2.4"))
        assertFalse(isPhpVersionValid(mock, "8.2.6"))
        assertTrue(isPhpVersionValid(mock, "8.2.5", includeEqual = true))
        assertFalse(isPhpVersionValid(mock, "8.2.5", includeEqual = false))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should handle single-digit minRequired`() {
        val mock = createMockPhpBinary("8.0.0")
        assertTrue(isPhpVersionValid(mock, "7"))
        assertTrue(isPhpVersionValid(mock, "8"))
        assertFalse(isPhpVersionValid(mock, "9"))
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should throw when binary outputs empty`() {
        val mock = File.createTempFile("mock-php-empty", ".sh").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "7.1")
        }
        mock.delete()
    }

    @Test
    fun `isPhpVersionValid should handle two-part minRequired against three-part current`() {
        val mock = createMockPhpBinary("8.2.0")
        assertTrue(isPhpVersionValid(mock, "8.2"))
        assertFalse(isPhpVersionValid(mock, "8.2", includeEqual = false))
        mock.delete()
    }

    // --- extractFileFromZip ---

    @Test
    fun `extractFileFromZip should extract matching entry`() {
        val zipBytes = createZipInMemory("data/hello.txt" to "hello world")
        val outPath = Files.createTempFile("extract", ".txt")

        val success = extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("data/hello.txt"))
        assertTrue(success)
        assertEquals("hello world", outPath.toFile().readText())
        outPath.toFile().delete()
    }

    @Test
    fun `extractFileFromZip should return false when entry not found`() {
        val zipBytes = createZipInMemory("a.txt" to "content")
        val outPath = Files.createTempFile("extract", ".txt")

        val success = extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("missing.txt"))
        assertFalse(success)
        outPath.toFile().delete()
    }

    @Test
    fun `extractFileFromZip should match multiple possible paths`() {
        val zipBytes = createZipInMemory("linux/bin" to "elf-data")
        val outPath = Files.createTempFile("extract", ".bin")

        val success = extractFileFromZip(
            ByteArrayInputStream(zipBytes), outPath,
            Path("windows/bin.exe"), Path("linux/bin")
        )
        assertTrue(success)
        assertEquals("elf-data", outPath.toFile().readText())
        outPath.toFile().delete()
    }

    @Test
    fun `extractFileFromZip should normalize backslash paths`() {
        val zipBytes = createZipInMemory("dir\\file.txt" to "backslash-content")
        val outPath = Files.createTempFile("extract", ".txt")

        val success = extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("dir/file.txt"))
        assertTrue(success)
        assertEquals("backslash-content", outPath.toFile().readText())
        outPath.toFile().delete()
    }

    // --- crc32ChecksumString ---

    @Test
    fun `Path crc32ChecksumString should return 8-char hex for existing file`() {
        val tempFile = Files.createTempFile("crc", ".txt")
        tempFile.toFile().writeText("test content")
        val checksum = tempFile.crc32ChecksumString
        assertNotNull(checksum)
        assertEquals(8, checksum.length)
        assertTrue(checksum.all { it in '0'..'9' || it in 'a'..'f' })
        tempFile.toFile().delete()
    }

    @Test
    fun `Path crc32ChecksumString should return null for nonexistent file`() {
        assertNull(Path("/tmp/nonexistent-crc32-file-xyz").crc32ChecksumString)
    }

    @Test
    fun `Path crc32ChecksumString should return null for directory`() {
        val tempDir = createTempDirectory("crc-dir")
        assertNull(tempDir.crc32ChecksumString)
        tempDir.toFile().delete()
    }

    @Test
    fun `Path crc32ChecksumString should be deterministic`() {
        val tempFile = Files.createTempFile("crc-det", ".txt")
        tempFile.toFile().writeText("deterministic")
        val c1 = tempFile.crc32ChecksumString
        val c2 = tempFile.crc32ChecksumString
        assertEquals(c1, c2)
        tempFile.toFile().delete()
    }

    @Test
    fun `File crc32ChecksumString should delegate to Path extension`() {
        val tempFile = Files.createTempFile("crc-file", ".txt").toFile()
        tempFile.writeText("file ext test")
        val fromPath = tempFile.toPath().crc32ChecksumString
        val fromFile = tempFile.crc32ChecksumString
        assertEquals(fromPath, fromFile)
        tempFile.delete()
    }

    @Test
    fun `File crc32ChecksumString should return null for nonexistent file`() {
        val fake = File("/tmp/nonexistent-crc32-file-xyz")
        assertNull(fake.crc32ChecksumString)
    }

    // --- helpers ---

    private fun createMockPhpBinary(version: String): File =
        File.createTempFile("mock-php", ".sh").apply {
            writeText("#!/bin/sh\necho 'PHP $version (cli) (built: Jan 1 2024 00:00:00) (NTS)'")
            setExecutable(true)
        }

    private fun createZipInMemory(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
