package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] construction — caller-supplied binaries and default initialization.
 *
 * - `should initialize with default parameters` — bundled/system PHP resolved
 * - `should throw when phpBinary does not exist` — invalid binary throws
 * - `should accept valid system php binary` — system PHP accepted
 * - `should accept custom parserBinary` — custom PHAR accepted
 * - `should throw when phpBinary version too low` — caller-supplied binary is never silently discarded
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
}
