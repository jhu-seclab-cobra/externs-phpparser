package edu.jhu.cobra.externs.phpparser

/*
 * Tests for [BinPhpParser] construction — caller-supplied binaries and default initialization.
 *
 * - `should initialize with default parameters` — bundled/system PHP resolved
 * - `should throw when phpBinary does not exist` — invalid binary throws
 * - `should accept valid system php binary` — system PHP accepted
 * - `should accept custom parserBinary` — custom PHAR accepted
 * - `should throw when phpBinary version too low` — caller-supplied binary is never silently discarded
 * - `should reject PHP 7_3` — 7.3.33, one release below the minimum 7.4
 * - `should accept PHP 7_4` — 7.4.0, the minimum the bundled PHAR runs on
 */

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class BinPhpParserConstructionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should initialize with default parameters`() {
        val parser = BinPhpParser()
        assertNotNull(parser)
    }

    @Test
    fun `should throw when phpBinary does not exist`() {
        assertFailsWith<ExternalBinaryInvalidException> {
            BinPhpParser(phpBinary = File("php-not-exist"))
        }
    }

    @Test
    fun `should accept valid system php binary`() {
        val phpFile = searchBin("php") ?: return
        val parser = BinPhpParser(phpBinary = phpFile)
        assertNotNull(parser)
    }

    @Test
    fun `should accept custom parserBinary`() {
        val fakePhar = Files.createTempFile(tempDir, "fake-parser", ".phar").toFile()
        fakePhar.writeText("fake")
        val parser = BinPhpParser(parserBinary = fakePhar)
        assertNotNull(parser)
    }

    @Test
    fun `should throw when phpBinary version too low`() {
        val fakePhp = Files.createTempFile(tempDir, "fake-php", ".sh").toFile()
        fakePhp.writeText("#!/bin/sh\necho 'PHP 5.0.0 (cli)'")
        fakePhp.setExecutable(true)
        val exception =
            assertFailsWith<ExternalBinaryInvalidException> {
                BinPhpParser(phpBinary = fakePhp)
            }
        assertTrue(
            exception.message.orEmpty().contains(fakePhp.absolutePath),
            "message should name the rejected binary: ${exception.message}",
        )
    }

    // Shell script printing the `php --version` banner of the given release.
    private fun fakePhpOfVersion(version: String): File =
        Files.createTempFile(tempDir, "fake-php", ".sh").toFile().apply {
            writeText("#!/bin/sh\necho 'PHP $version (cli)'")
            setExecutable(true)
        }

    @Test
    fun `should reject PHP 7_3`() {
        assertFailsWith<ExternalBinaryInvalidException> {
            BinPhpParser(phpBinary = fakePhpOfVersion("7.3.33"))
        }
    }

    @Test
    fun `should accept PHP 7_4`() {
        assertNotNull(BinPhpParser(phpBinary = fakePhpOfVersion("7.4.0")))
    }
}
