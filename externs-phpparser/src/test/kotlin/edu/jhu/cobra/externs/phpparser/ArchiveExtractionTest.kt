package edu.jhu.cobra.externs.phpparser

/*
 * Tests for ArchiveExtraction.kt — ZIP entry extraction, atomic publication, and CRC32 checksums.
 *
 * - `extractFileFromZip should extract matching entry` — extracts target file from ZIP.
 * - `extractFileFromZip should throw when entry not found` — missing entry throws naming the target.
 * - `extractFileFromZip should match multiple possible paths` — matches any of multiple candidate paths.
 * - `extractFileFromZip should normalize backslash paths` — backslash-to-forward-slash normalization.
 * - `extractFileFromZip should leave no staging file behind` — the staged sibling is gone after extraction.
 * - `extractFileFromZip should never expose a partial target to concurrent readers` — a reader racing
 *   the extraction only ever observes the old or the new content, never a missing or partial file.
 * - `extractFileFromZip should throw for an empty archive` — zero-entry ZIP is a not-found, not a crash.
 * - `extractFileFromZip should propagate IOException for a truncated archive` — corrupt input fails
 *   the extraction and publishes nothing.
 * - `extractFileFromZip should extract a zero-byte entry as an empty file` — degenerate entry size.
 * - `Path crc32ChecksumString should return 8-char hex for existing file` — valid checksum format.
 * - `Path crc32ChecksumString should return null for nonexistent file` — null for missing file.
 * - `Path crc32ChecksumString should return null for directory` — null for directory path.
 * - `Path crc32ChecksumString should be deterministic` — same file produces same checksum.
 * - `File crc32ChecksumString should delegate to Path extension` — File extension matches Path extension.
 * - `File crc32ChecksumString should return null for nonexistent file` — null for missing file via File extension.
 * - `Path crc32ChecksumString should match the CRC32 check value` — "123456789" hashes to cbf43926.
 * - `Path crc32ChecksumString should return zero for an empty file` — empty input hashes to 00000000.
 */

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// New content large enough to keep the extraction window open while the reader samples it.
private const val RACE_NEW_CONTENT_SIZE = 16 shl 20

private const val RACE_OLD_CONTENT_SIZE = 1 shl 20

internal class ArchiveExtractionTest {
    @TempDir
    lateinit var tempDir: Path

    // --- extractFileFromZip ---

    @Test
    fun `extractFileFromZip should extract matching entry`() {
        val zipBytes = createZipInMemory("data/hello.txt" to "hello world".toByteArray())
        val outPath = tempDir.resolve("extract.txt")

        extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("data/hello.txt"))
        assertEquals("hello world", outPath.toFile().readText())
    }

    @Test
    fun `extractFileFromZip should throw when entry not found`() {
        val zipBytes = createZipInMemory("a.txt" to "content".toByteArray())
        val outPath = tempDir.resolve("extract.txt")

        val exception =
            assertFailsWith<ExternalBinaryNotFoundException> {
                extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("missing.txt"))
            }
        assertTrue("missing.txt" in exception.message.orEmpty(), "message should name the target: ${exception.message}")
    }

    @Test
    fun `extractFileFromZip should match multiple possible paths`() {
        val zipBytes = createZipInMemory("linux/bin" to "elf-data".toByteArray())
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
        val zipBytes = createZipInMemory("dir\\file.txt" to "backslash-content".toByteArray())
        val outPath = tempDir.resolve("extract.txt")

        extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("dir/file.txt"))
        assertEquals("backslash-content", outPath.toFile().readText())
    }

    @Test
    fun `extractFileFromZip should leave no staging file behind`() {
        val zipBytes = createZipInMemory("bin" to "payload".toByteArray())
        val outPath = tempDir.resolve("extract.bin")

        extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("bin"))
        assertEquals(
            listOf(outPath),
            tempDir.listDirectoryEntries(),
            "only the published target may remain after extraction",
        )
    }

    @Test
    fun `extractFileFromZip should never expose a partial target to concurrent readers`() {
        val oldContent = ByteArray(RACE_OLD_CONTENT_SIZE) { 'a'.code.toByte() }
        val newContent = ByteArray(RACE_NEW_CONTENT_SIZE) { 'b'.code.toByte() }
        val target = tempDir.resolve("racing.bin")
        Files.write(target, oldContent)
        val zipBytes = createZipInMemory("bin" to newContent)

        var violation: String? = null
        val extractor = thread { extractFileFromZip(ByteArrayInputStream(zipBytes), target, Path("bin")) }
        while (extractor.isAlive && violation == null) {
            violation = observeRaceViolation(target, oldContent.size, newContent.size)
        }
        extractor.join()
        assertNull(violation, "reader racing the extraction observed a non-atomic state")
        assertContentEquals(newContent, Files.readAllBytes(target))
    }

    @Test
    fun `extractFileFromZip should throw for an empty archive`() {
        val zipBytes = createZipInMemory()
        val outPath = tempDir.resolve("extract.bin")

        assertFailsWith<ExternalBinaryNotFoundException> {
            extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("bin"))
        }
    }

    @Test
    fun `extractFileFromZip should propagate IOException for a truncated archive`() {
        // Incompressible payload keeps the compressed entry large, so half the bytes cut mid-entry.
        val payload = ByteArray(65536) { (it * 31 % 251).toByte() }
        val zipBytes = createZipInMemory("bin" to payload)
        val truncated = zipBytes.copyOf(zipBytes.size / 2)
        val outPath = tempDir.resolve("extract.bin")

        assertFailsWith<IOException> {
            extractFileFromZip(ByteArrayInputStream(truncated), outPath, Path("bin"))
        }
        assertEquals(
            emptyList(),
            tempDir.listDirectoryEntries(),
            "a failed extraction must publish nothing and leave no staging file",
        )
    }

    @Test
    fun `extractFileFromZip should extract a zero-byte entry as an empty file`() {
        val zipBytes = createZipInMemory("empty.bin" to ByteArray(0))
        val outPath = tempDir.resolve("extract.bin")

        extractFileFromZip(ByteArrayInputStream(zipBytes), outPath, Path("empty.bin"))
        assertEquals(0L, Files.size(outPath))
    }

    // Returns a description of an observed non-atomic state, or null when the read was consistent.
    private fun observeRaceViolation(
        target: Path,
        oldSize: Int,
        newSize: Int,
    ): String? {
        val observedSize =
            try {
                Files.size(target)
            } catch (missing: NoSuchFileException) {
                return "target vanished mid-extraction: ${missing.file}"
            }
        if (observedSize != oldSize.toLong() && observedSize != newSize.toLong()) {
            return "partial target of $observedSize bytes"
        }
        return null
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

    @Test
    fun `Path crc32ChecksumString should match the CRC32 check value`() {
        // "123456789" -> cbf43926 is the standard CRC-32 check value.
        val tempFile = Files.createTempFile(tempDir, "crc-vector", ".txt")
        tempFile.toFile().writeText("123456789")
        assertEquals("cbf43926", tempFile.crc32ChecksumString)
    }

    @Test
    fun `Path crc32ChecksumString should return zero for an empty file`() {
        val tempFile = Files.createTempFile(tempDir, "crc-empty", ".txt")
        assertEquals("00000000", tempFile.crc32ChecksumString)
    }

    // --- helpers ---

    private fun createZipInMemory(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
