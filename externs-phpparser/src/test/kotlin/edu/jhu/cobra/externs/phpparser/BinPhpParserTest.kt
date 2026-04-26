package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] — PHP-specific binary resolver and AST parser.
 *
 * - `should initialize with default parameters` — construction succeeds with bundled/system PHP.
 * - `should throw when phpBinary does not exist` — invalid phpBinary throws ExternalBinaryInvalidException.
 * - `should accept valid system php binary` — system PHP binary accepted if present.
 * - `should accept custom parserBinary` — user-provided PHAR accepted.
 * - `should default to S_EXPR dump type` — default dumpType is S_EXPR.
 * - `should configure all dump types` — all DumpType enum values settable.
 * - `DumpType toString should return opt string` — toString returns CLI flag string.
 * - `should throw when target is not set` — execute without target throws ExternalBinaryArgumentMissException.
 * - `should parse PHP file with JSON dump` — JSON dump produces output containing Stmt_Echo.
 * - `should parse PHP file with S_EXPR dump` — S_EXPR dump produces non-empty output.
 * - `should parse PHP file with VAR dump` — VAR dump produces non-empty output.
 * - `should include positions when doWithPositions is true` — positions option accepted.
 * - `should include pretty print output` — pretty-print option accepted.
 * - `should enable recovery mode` — recovery mode parses broken PHP.
 * - `should set name resolution option in command array` — --resolve-others in command array.
 * - `should enable column info` — column-info option accepted.
 * - `should include all boolean options in command array` — all flags present in command array.
 * - `should produce correct command array with no options enabled` — dump type and target at end.
 * - `should parse complex PHP code with class and function` — class/method nodes in JSON output.
 * - `should use cache on second execution with same command` — cached output file reused.
 * - `should restore config after executeWith` — dumpType restored after executeWith.
 * - `should skip false boolean options in command array` — false booleans excluded from command.
 * - `should fall back to auto-detect when phpBinary version too low` — invalid version falls through to auto-detect.
 * - `should fall back to system PATH when bundled PHP zip missing` — mocked null resource triggers searchBin fallback.
 * - `should fall back to system PATH with valid PHP found` — system PATH search returns valid PHP.
 * - `should throw when bundled zip missing and no system PHP` — no bundled zip and no system PHP throws.
 * - `should throw when system PHP version too low in fallback` — system PHP found but version invalid throws.
 * - `should extract bundled PHP from zip when CRC32 mismatch` — mocked zip extraction succeeds.
 * - `should throw when PHP zip extraction fails` — extraction returning false throws.
 * - `should throw when parser zip extraction fails` — parser extraction failure throws.
 */

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BinPhpParserTest {

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
        val fakePhar = Files.createTempFile("fake-parser", ".phar").toFile()
        fakePhar.writeText("fake")
        val parser = BinPhpParser(parserBinary = fakePhar)
        assertNotNull(parser)
        fakePhar.delete()
    }

    @Test
    fun `should default to S_EXPR dump type`() {
        val parser = BinPhpParser()
        assertEquals(BinPhpParser.DumpType.S_EXPR, parser.dumpType)
    }

    @Test
    fun `should configure all dump types`() {
        val parser = BinPhpParser()
        for (type in BinPhpParser.DumpType.entries) {
            parser.dumpType = type
            assertEquals(type, parser.dumpType)
        }
    }

    @Test
    fun `DumpType toString should return opt string`() {
        assertEquals("--dump", BinPhpParser.DumpType.S_EXPR.toString())
        assertEquals("--var-dump", BinPhpParser.DumpType.VAR.toString())
        assertEquals("--json-dump", BinPhpParser.DumpType.JSON.toString())
    }

    @Test
    fun `should throw when target is not set`() {
        val parser = BinPhpParser()
        assertFailsWith<ExternalBinaryArgumentMissException> {
            parser.execute()
        }
    }

    @Test
    fun `should parse PHP file with JSON dump`() {
        val phpFile = createTempPhpFile("<?php echo 'hello';")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.exists())
        val output = result.output.readText()
        assertTrue(output.contains("Stmt_Echo"))
        phpFile.delete()
    }

    @Test
    fun `should parse PHP file with S_EXPR dump`() {
        val phpFile = createTempPhpFile("<?php \$x = 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.S_EXPR
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
        phpFile.delete()
    }

    @Test
    fun `should parse PHP file with VAR dump`() {
        val phpFile = createTempPhpFile("<?php \$x = 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.VAR
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
        phpFile.delete()
    }

    @Test
    fun `should include positions when doWithPositions is true`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithPositions = true
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should include pretty print output`() {
        val phpFile = createTempPhpFile("<?php function foo() { return 1; }")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doPrettyPrint = true
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should enable recovery mode`() {
        val phpFile = createTempPhpFile("<?php echo 'unclosed")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithRecovery = true
        }

        val result = parser.execute()
        assertTrue(result.output.exists())
        phpFile.delete()
    }

    @Test
    fun `should set name resolution option in command array`() {
        val phpFile = createTempPhpFile("<?php namespace App; class Foo {}")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doResolveName = true
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--resolve-others"))
        phpFile.delete()
    }

    @Test
    fun `should enable column info`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithColInfo = true
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should include all boolean options in command array`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doPrettyPrint = true
            doResolveName = true
            doWithColInfo = true
            doWithPositions = true
            doWithRecovery = true
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--pretty-print"))
        assertTrue(cmd.contains("--resolve-others"))
        assertTrue(cmd.contains("--with-column-info"))
        assertTrue(cmd.contains("--with-positions"))
        assertTrue(cmd.contains("--with-recovery"))
        assertTrue(cmd.contains("--json-dump"))
        phpFile.delete()
    }

    @Test
    fun `should produce correct command array with no options enabled`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.size >= 4)
        assertEquals("--json-dump", cmd[cmd.size - 2])
        assertEquals(phpFile.absolutePath, cmd.last())
    }

    @Test
    fun `should parse complex PHP code with class and function`() {
        val phpFile = createTempPhpFile(
            """
            <?php
            namespace App\Models;

            class User {
                private string ${'$'}name;
                public function __construct(string ${'$'}name) {
                    ${'$'}this->name = ${'$'}name;
                }
                public function getName(): string {
                    return ${'$'}this->name;
                }
            }
            """.trimIndent()
        )
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        val output = result.output.readText()
        assertTrue(output.contains("Stmt_Class"))
        assertTrue(output.contains("Stmt_ClassMethod"))
        phpFile.delete()
    }

    @Test
    fun `should use cache on second execution with same command`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doCacheOutput = true
        }

        val r1 = parser.execute()
        assertEquals(0, r1.code)
        val r2 = parser.execute()
        assertEquals(0, r2.code)
        assertEquals(r1.output.absolutePath, r2.output.absolutePath)
        phpFile.delete()
    }

    @Test
    fun `should restore config after executeWith`() {
        val phpFile1 = createTempPhpFile("<?php echo 1;")
        val phpFile2 = createTempPhpFile("<?php echo 2;")
        val parser = BinPhpParser().apply {
            target = phpFile1
            dumpType = BinPhpParser.DumpType.S_EXPR
        }

        val result = parser.executeWith {
            target = phpFile2
            dumpType = BinPhpParser.DumpType.JSON
        }
        assertNotNull(result)
        assertEquals(BinPhpParser.DumpType.S_EXPR, parser.dumpType)
        phpFile1.delete()
        phpFile2.delete()
    }

    @Test
    fun `should fall back to auto-detect when phpBinary version too low`() {
        val fakePhp = Files.createTempFile("fake-php", ".sh").toFile()
        fakePhp.writeText("#!/bin/sh\necho 'PHP 5.0.0 (cli)'")
        fakePhp.setExecutable(true)
        try {
            val parser = BinPhpParser(phpBinary = fakePhp)
            assertNotNull(parser)
        } finally {
            fakePhp.delete()
        }
    }

    @Test
    fun `should skip false boolean options in command array`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithRecovery = true
            doPrettyPrint = false
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--with-recovery"))
        assertFalse(cmd.contains("--pretty-print"))
        phpFile.delete()
    }

    @Test
    fun `should fall back to system PATH when bundled PHP zip missing`() {
        val sysPhp = searchBin("php") ?: return
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns sysPhp
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                val parser = BinPhpParser()
                assertNotNull(parser)
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should fall back to system PATH with valid PHP found`() {
        val fakeBin = Files.createTempFile("fake-php", "").toFile()
        fakeBin.writeText("fake-php")
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns fakeBin
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                val parser = BinPhpParser()
                assertNotNull(parser)
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
            fakeBin.delete()
        }
    }

    @Test
    fun `should throw when system PHP version too low in fallback`() {
        val fakeBin = Files.createTempFile("fake-php", "").toFile()
        fakeBin.writeText("fake-php")
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns false
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns fakeBin
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
            fakeBin.delete()
        }
    }

    @Test
    fun `should throw when bundled zip missing and no system PHP`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns null
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should extract bundled PHP from zip when CRC32 mismatch`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true

            val parser = BinPhpParser()
            assertNotNull(parser)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should throw when PHP zip extraction fails`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns false

            assertFailsWith<ExternalBinaryNotFoundException> {
                BinPhpParser()
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should throw when parser zip extraction fails`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            var callCount = 0
            every { extractFileFromZip(any(), any(), *anyVararg()) } answers {
                callCount++
                callCount == 1
            }

            assertFailsWith<ExternalBinaryNotFoundException> {
                BinPhpParser()
            }
        } finally {
            unmockkAll()
        }
    }

    private fun createTempPhpFile(code: String): File =
        Files.createTempFile("test", ".php").toFile().apply { writeText(code) }
}
