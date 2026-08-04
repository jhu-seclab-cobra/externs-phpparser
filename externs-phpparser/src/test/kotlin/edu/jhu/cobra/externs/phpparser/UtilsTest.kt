package edu.jhu.cobra.externs.phpparser

/**
 * Tests for utility functions in Utils.kt — binary search, version validation, ZIP extraction, CRC32.
 *
 * - `searchBin under directory should find executable direct child` — finds executable at root of directory.
 * - `searchBin under directory should not find file in subdirectory` — nested files are never PATH hits.
 * - `searchBin under directory should ignore non-executable file` — executable bit is required.
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
 * - `isPhpVersionValid should attach IOException cause when binary cannot run` — process-start failure keeps cause.
 * - `isPhpVersionValid should report timeout when version probe hangs` — a hung probe throws a distinct
 *   "version probe timed out" reason instead of misreporting unparsable output.
 * - `isPhpVersionValid should compare major version correctly` — major-only comparison.
 * - `isPhpVersionValid should compare minor version when major is equal` — minor comparison.
 * - `isPhpVersionValid should compare patch version when major and minor are equal` — patch comparison.
 * - `isPhpVersionValid should handle single-digit minRequired` — single-component version.
 * - `isPhpVersionValid should throw when binary outputs empty` — empty output throws.
 * - `isPhpVersionValid should handle two-part minRequired against three-part current` — mixed lengths.
 * - `extractFileFromZip should extract matching entry` — extracts target file from ZIP.
 * - `extractFileFromZip should throw when entry not found` — missing entry throws naming the target.
 * - `extractFileFromZip should match multiple possible paths` — matches any of multiple candidate paths.
 * - `extractFileFromZip should normalize backslash paths` — backslash-to-forward-slash normalization.
 * - `Path crc32ChecksumString should return 8-char hex for existing file` — valid checksum format.
 * - `Path crc32ChecksumString should return null for nonexistent file` — null for missing file.
 * - `Path crc32ChecksumString should return null for directory` — null for directory path.
 * - `Path crc32ChecksumString should be deterministic` — same file produces same checksum.
 * - `File crc32ChecksumString should delegate to Path extension` — File extension matches Path extension.
 * - `File crc32ChecksumString should return null for nonexistent file` — null for missing file via File extension.
 */

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class UtilsTest {
    @TempDir
    lateinit var tempDir: Path

    // --- searchBin(under, possibleNames) ---

    @Test
    fun `searchBin under directory should find executable direct child`() {
        val target =
            tempDir.resolve("mybin").toFile().apply {
                createNewFile()
                setExecutable(true)
            }
        val found = searchBin(tempDir, "mybin")
        assertNotNull(found)
        assertEquals(target.absolutePath, found.absolutePath)
    }

    @Test
    fun `searchBin under directory should not find file in subdirectory`() {
        val subDir = tempDir.resolve("sub").apply { createDirectories() }
        subDir.resolve("deep.bin").toFile().apply {
            createNewFile()
            setExecutable(true)
        }
        assertNull(searchBin(tempDir, "deep.bin"), "a nested file must never be treated as a PATH entry hit")
    }

    @Test
    fun `searchBin under directory should ignore non-executable file`() {
        tempDir.resolve("plainfile").toFile().apply {
            createNewFile()
            setExecutable(false)
        }
        assertNull(searchBin(tempDir, "plainfile"))
    }

    @Test
    fun `searchBin under directory should return null for missing file`() {
        assertNull(searchBin(tempDir, "nonexistent"))
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
    }

    @Test
    fun `isPhpVersionValid should return true when versions are equal and includeEqual is true`() {
        val mock = createMockPhpBinary("7.4.10")
        assertTrue(isPhpVersionValid(mock, "7.4.10", includeEqual = true))
    }

    @Test
    fun `isPhpVersionValid should return false when versions are equal and includeEqual is false`() {
        val mock = createMockPhpBinary("7.4.10")
        assertFalse(isPhpVersionValid(mock, "7.4.10", includeEqual = false))
    }

    @Test
    fun `isPhpVersionValid should return false when current version is lower`() {
        val mock = createMockPhpBinary("7.4.10")
        assertFalse(isPhpVersionValid(mock, "8.0"))
        assertFalse(isPhpVersionValid(mock, "7.5"))
        assertFalse(isPhpVersionValid(mock, "7.4.11"))
    }

    @Test
    fun `isPhpVersionValid should handle incomplete version numbers`() {
        val mock = createMockPhpBinary("8.1.0")
        assertTrue(isPhpVersionValid(mock, "8"))
        assertTrue(isPhpVersionValid(mock, "8.1"))
        assertFalse(isPhpVersionValid(mock, "8.2"))
    }

    @Test
    fun `isPhpVersionValid should throw on invalid minRequired format`() {
        val mock = createMockPhpBinary("8.0.0")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "invalid.version")
        }
    }

    @Test
    fun `isPhpVersionValid should throw when binary produces no version output`() {
        val mock = createMockScript("mock-php-bad.sh", "#!/bin/sh\necho 'not a version'")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should throw when binary does not exist`() {
        val fake = File("/tmp/nonexistent-php-binary-xyz")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(fake, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should attach IOException cause when binary cannot run`() {
        val fake = File("/tmp/nonexistent-php-binary-xyz")
        val exception =
            assertFailsWith<ExternalBinaryInvalidException> {
                isPhpVersionValid(fake, "7.1")
            }
        assertTrue(exception.cause is java.io.IOException, "expected IOException cause, got: ${exception.cause}")
    }

    @Test
    fun `isPhpVersionValid should report timeout when version probe hangs`() {
        // Sleeps well past the 10s probe backstop; the probe must kill it and name the timeout.
        val mock = createMockScript("mock-php-hang.sh", "#!/bin/sh\nsleep 30\necho 'PHP 8.0.0 (cli)'")
        val exception =
            assertFailsWith<ExternalBinaryInvalidException> {
                isPhpVersionValid(mock, "7.1")
            }
        assertTrue(
            "version probe timed out" in exception.message.orEmpty(),
            "hung probe must report a timeout, not unparsable output: ${exception.message}",
        )
    }

    @Test
    fun `isPhpVersionValid should compare major version correctly`() {
        val mock = createMockPhpBinary("8.0.0")
        assertTrue(isPhpVersionValid(mock, "7.0.0"))
        assertFalse(isPhpVersionValid(mock, "9.0.0"))
    }

    @Test
    fun `isPhpVersionValid should compare minor version when major is equal`() {
        val mock = createMockPhpBinary("8.2.0")
        assertTrue(isPhpVersionValid(mock, "8.1.0"))
        assertFalse(isPhpVersionValid(mock, "8.3.0"))
    }

    @Test
    fun `isPhpVersionValid should compare patch version when major and minor are equal`() {
        val mock = createMockPhpBinary("8.2.5")
        assertTrue(isPhpVersionValid(mock, "8.2.4"))
        assertFalse(isPhpVersionValid(mock, "8.2.6"))
        assertTrue(isPhpVersionValid(mock, "8.2.5", includeEqual = true))
        assertFalse(isPhpVersionValid(mock, "8.2.5", includeEqual = false))
    }

    @Test
    fun `isPhpVersionValid should handle single-digit minRequired`() {
        val mock = createMockPhpBinary("8.0.0")
        assertTrue(isPhpVersionValid(mock, "7"))
        assertTrue(isPhpVersionValid(mock, "8"))
        assertFalse(isPhpVersionValid(mock, "9"))
    }

    @Test
    fun `isPhpVersionValid should throw when binary outputs empty`() {
        val mock = createMockScript("mock-php-empty.sh", "#!/bin/sh\n")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should handle two-part minRequired against three-part current`() {
        val mock = createMockPhpBinary("8.2.0")
        assertTrue(isPhpVersionValid(mock, "8.2"))
        assertFalse(isPhpVersionValid(mock, "8.2", includeEqual = false))
    }

    // --- extractFileFromZip ---

    @Test
    fun `extractFileFromZip should extract matching entry`() {
        val zipBytes = createZipInMemory("data/hello.txt" to "hello world")
        val outPath = tempDir.resolve("extract.txt")

        extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("data/hello.txt"))
        assertEquals("hello world", outPath.toFile().readText())
    }

    @Test
    fun `extractFileFromZip should throw when entry not found`() {
        val zipBytes = createZipInMemory("a.txt" to "content")
        val outPath = tempDir.resolve("extract.txt")

        val exception =
            assertFailsWith<ExternalBinaryNotFoundException> {
                extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("missing.txt"))
            }
        assertTrue("missing.txt" in exception.message.orEmpty(), "message should name the target: ${exception.message}")
    }

    @Test
    fun `extractFileFromZip should match multiple possible paths`() {
        val zipBytes = createZipInMemory("linux/bin" to "elf-data")
        val outPath = tempDir.resolve("extract.bin")

        extractFileFromZip(
            ByteArrayInputStream(zipBytes),
            outPath,
            Path("windows/bin.exe"),
            Path("linux/bin"),
        )
        assertEquals("elf-data", outPath.toFile().readText())
    }

    @Test
    fun `extractFileFromZip should normalize backslash paths`() {
        val zipBytes = createZipInMemory("dir\\file.txt" to "backslash-content")
        val outPath = tempDir.resolve("extract.txt")

        extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("dir/file.txt"))
        assertEquals("backslash-content", outPath.toFile().readText())
    }

    // --- crc32ChecksumString ---

    @Test
    fun `Path crc32ChecksumString should return 8-char hex for existing file`() {
        val tempFile = Files.createTempFile(tempDir, "crc", ".txt")
        tempFile.toFile().writeText("test content")
        val checksum = tempFile.crc32ChecksumString
        assertNotNull(checksum)
        assertEquals(8, checksum.length)
        assertTrue(checksum.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `Path crc32ChecksumString should return null for nonexistent file`() {
        assertNull(Path("/tmp/nonexistent-crc32-file-xyz").crc32ChecksumString)
    }

    @Test
    fun `Path crc32ChecksumString should return null for directory`() {
        assertNull(tempDir.crc32ChecksumString)
    }

    @Test
    fun `Path crc32ChecksumString should be deterministic`() {
        val tempFile = Files.createTempFile(tempDir, "crc-det", ".txt")
        tempFile.toFile().writeText("deterministic")
        val c1 = tempFile.crc32ChecksumString
        val c2 = tempFile.crc32ChecksumString
        assertEquals(c1, c2)
    }

    @Test
    fun `File crc32ChecksumString should delegate to Path extension`() {
        val tempFile = Files.createTempFile(tempDir, "crc-file", ".txt").toFile()
        tempFile.writeText("file ext test")
        val fromPath = tempFile.toPath().crc32ChecksumString
        val fromFile = tempFile.crc32ChecksumString
        assertEquals(fromPath, fromFile)
    }

    @Test
    fun `File crc32ChecksumString should return null for nonexistent file`() {
        val fake = File("/tmp/nonexistent-crc32-file-xyz")
        assertNull(fake.crc32ChecksumString)
    }

    // --- helpers ---

    private fun createMockPhpBinary(version: String): File =
        createMockScript(
            "mock-php-$version.sh",
            "#!/bin/sh\necho 'PHP $version (cli) (built: Jan 1 2024 00:00:00) (NTS)'",
        )

    private fun createMockScript(
        name: String,
        script: String,
    ): File =
        tempDir.resolve(name).toFile().apply {
            writeText(script)
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
