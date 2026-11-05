package edu.jhu.cobra.externs.phpparser

/*
 * Tests for [BinPhpParser] configuration — dump types and command array construction.
 *
 * - `should default to S_EXPR dump type` — default dumpType
 * - `should configure all dump types` — all DumpType values
 * - `DumpType opt should return CLI flag string` — opt carries the CLI flag
 * - `should include all boolean options in command array` — all flags present
 * - `should produce correct command array with no options enabled` — minimal command
 * - `should skip false boolean options in command array` — false excluded
 * - `should set name resolution option in command array` — --resolve-names flag
 * - `should run the dump script against the parser binary` — interpreter, dump script, parser PHAR order
 * - `command array should match the declared layout for every flag and dump type combination` — 96 targets
 * - `should default every flag to false and read an unset target as a missing argument` — defaults and required target
 */

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BinPhpParserConfigurationTest {
    @TempDir
    lateinit var tempDir: Path

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
    fun `DumpType opt should return CLI flag string`() {
        assertEquals("--dump", BinPhpParser.DumpType.S_EXPR.opt)
        assertEquals("--var-dump", BinPhpParser.DumpType.VAR.opt)
        assertEquals("--json-dump", BinPhpParser.DumpType.JSON.opt)
    }

    @Test
    fun `should include all boolean options in command array`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
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
        assertTrue(cmd.contains("--resolve-names"))
        assertTrue(cmd.contains("--with-column-info"))
        assertTrue(cmd.contains("--with-positions"))
        assertTrue(cmd.contains("--with-recovery"))
        assertTrue(cmd.contains("--json-dump"))
    }

    @Test
    fun `should produce correct command array with no options enabled`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
            }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.size >= 4)
        assertEquals("--json-dump", cmd[cmd.size - 2])
        assertEquals(phpFile.absolutePath, cmd.last())
    }

    @Test
    fun `should skip false boolean options in command array`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doWithRecovery = true
                doPrettyPrint = false
            }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--with-recovery"))
        assertFalse(cmd.contains("--pretty-print"))
    }

    @Test
    fun `should set name resolution option in command array`() {
        val phpFile = createPhpFile(tempDir, "<?php namespace App; class Foo {}")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doResolveName = true
            }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--resolve-names"))
    }

    @Test
    fun `should run the dump script against the parser binary`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
            }

        val cmd = parser.getCommandArray()
        assertTrue(File(cmd[1]).name.matches(Regex("php_parse-[0-9a-f]{8}\\.php")), "Dump script expected: ${cmd.toList()}")
        assertEquals("php-parser-5.7.0", File(cmd[2]).name)
    }

    @Test
    fun `command array should match the declared layout for every flag and dump type combination`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val flagNames =
            listOf("--pretty-print", "--resolve-names", "--with-column-info", "--with-positions", "--with-recovery")
        val prefix = BinPhpParser().apply { target = phpFile }.getCommandArray().take(3)
        for (type in BinPhpParser.DumpType.entries) {
            for (mask in 0 until (1 shl flagNames.size)) {
                val parser =
                    BinPhpParser().apply {
                        target = phpFile
                        dumpType = type
                        doPrettyPrint = mask and 1 != 0
                        doResolveName = mask and 2 != 0
                        doWithColInfo = mask and 4 != 0
                        doWithPositions = mask and 8 != 0
                        doWithRecovery = mask and 16 != 0
                    }
                val enabled = flagNames.filterIndexed { bit, _ -> mask and (1 shl bit) != 0 }
                val expected = prefix + enabled + type.opt + phpFile.absolutePath
                assertEquals(expected, parser.getCommandArray().toList(), "type=$type mask=$mask")
            }
        }
    }

    @Test
    fun `should default every flag to false and read an unset target as a missing argument`() {
        val parser = BinPhpParser()
        assertFalse(parser.doPrettyPrint)
        assertFalse(parser.doResolveName)
        assertFalse(parser.doWithColInfo)
        assertFalse(parser.doWithPositions)
        assertFalse(parser.doWithRecovery)
        assertFailsWith<ExternalBinaryArgumentMissException> { parser.target }
        assertFailsWith<ExternalBinaryArgumentMissException> { parser.getCommandArray() }
    }
}
