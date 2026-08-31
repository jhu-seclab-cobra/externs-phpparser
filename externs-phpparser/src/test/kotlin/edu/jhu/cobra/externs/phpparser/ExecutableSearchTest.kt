package edu.jhu.cobra.externs.phpparser

/**
 * Tests for executable lookup in ExecutableSearch.kt — directory children and system PATH.
 *
 * - `searchBin under directory should find executable direct child` — finds executable at root of directory.
 * - `searchBin under directory should not find file in subdirectory` — nested files are never PATH hits.
 * - `searchBin under directory should ignore non-executable file` — executable bit is required.
 * - `searchBin under directory should return null for missing file` — returns null when no match.
 * - `searchBin by name should find php on PATH as an executable file` — system PATH search for php;
 *   skipped when no php is installed.
 * - `searchBin by name should return null for nonexistent binary` — returns null for unknown binary.
 * - `searchBin under directory should return first matching candidate` — candidate order decides
 *   which of several executables wins.
 */

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ExecutableSearchTest {
    @TempDir
    lateinit var tempDir: Path

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

    @Test
    fun `searchBin by name should find php on PATH as an executable file`() {
        val result = searchBin("php")
        assumeTrue(result != null, "php is not installed on this machine; nothing to assert")
        assertNotNull(result)
        assertTrue(result.isFile && result.canExecute(), "PATH hit must be an executable regular file")
    }

    @Test
    fun `searchBin by name should return null for nonexistent binary`() {
        assertNull(searchBin("totally-nonexistent-binary-xyz-999"))
    }

    @Test
    fun `searchBin under directory should return first matching candidate`() {
        val first =
            tempDir.resolve("bin-a").toFile().apply {
                createNewFile()
                setExecutable(true)
            }
        tempDir.resolve("bin-b").toFile().apply {
            createNewFile()
            setExecutable(true)
        }
        val found = searchBin(tempDir, "bin-a", "bin-b")
        assertNotNull(found)
        assertEquals(first.absolutePath, found.absolutePath, "candidate order decides the winner")
    }
}
