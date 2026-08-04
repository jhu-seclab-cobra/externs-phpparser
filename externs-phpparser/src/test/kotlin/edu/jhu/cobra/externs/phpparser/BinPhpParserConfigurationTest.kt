package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] configuration — dump types and command array construction.
 *
 * - `should default to S_EXPR dump type` — default dumpType
 * - `should configure all dump types` — all DumpType values
 * - `DumpType opt should return CLI flag string` — opt carries the CLI flag
 * - `should include all boolean options in command array` — all flags present
 * - `should produce correct command array with no options enabled` — minimal command
 * - `should skip false boolean options in command array` — false excluded
 * - `should set name resolution option in command array` — --resolve-names flag
 */

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
