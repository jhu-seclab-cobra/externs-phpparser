package edu.jhu.cobra.externs.phpparser

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private fun createTempPhpFile(code: String): File =
        Files.createTempFile("test", ".php").toFile().apply { writeText(code) }
}
