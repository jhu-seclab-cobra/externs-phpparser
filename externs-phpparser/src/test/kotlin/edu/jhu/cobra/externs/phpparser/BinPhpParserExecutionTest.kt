package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] execution — dump output, caching, recovery, and edge-case inputs.
 *
 * Execution:
 * - `should throw when target is not set` — missing target throws
 * - `should parse PHP file with JSON dump` — JSON output
 * - `should parse PHP file with S_EXPR dump` — S-expr output
 * - `should parse PHP file with VAR dump` — var_dump output
 * - `should include positions when doWithPositions is true` — position attributes
 * - `should include pretty print output` — pretty-print accepted
 * - `should enable recovery mode` — broken PHP parsed
 * - `should enable column info` — column info accepted
 * - `should parse complex PHP code with class and function` — class/method nodes
 * - `should use cache on second execution with same command` — cache hit in an isolated workTmpDir
 * - `should restore config after executeWith` — config restored
 *
 * Recovery:
 * - `recovery should parse broken PHP without crashing` — recovery produces output for broken PHP
 * - `recovery should handle unclosed string` — partial AST
 *
 * Edge cases:
 * - `should parse empty PHP file` — empty file succeeds
 * - `should parse PHP file with only HTML` — HTML-only succeeds
 * - `should parse PHP 8 features` — enum/union type/readonly
 */

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class BinPhpParserExecutionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should throw when target is not set`() {
        val parser = BinPhpParser()
        assertFailsWith<ExternalBinaryArgumentMissException> {
            parser.execute()
        }
    }

    @Test
    fun `should parse PHP file with JSON dump`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 'hello';")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
            }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.exists())
        val output = result.output.readText()
        assertTrue(output.contains("Stmt_Echo"))
    }

    @Test
    fun `should parse PHP file with S_EXPR dump`() {
        val phpFile = createPhpFile(tempDir, "<?php \$x = 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.S_EXPR
            }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
    }

    @Test
    fun `should parse PHP file with VAR dump`() {
        val phpFile = createPhpFile(tempDir, "<?php \$x = 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.VAR
            }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
    }

    @Test
    fun `should include positions when doWithPositions is true`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doWithPositions = true
            }

        val result = parser.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `should include pretty print output`() {
        val phpFile = createPhpFile(tempDir, "<?php function foo() { return 1; }")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doPrettyPrint = true
            }

        val result = parser.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `should enable recovery mode`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 'unclosed")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doWithRecovery = true
            }

        val result = parser.execute()
        assertTrue(result.output.exists())
    }

    @Test
    fun `should enable column info`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doWithColInfo = true
            }

        val result = parser.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `should parse complex PHP code with class and function`() {
        val phpFile =
            createPhpFile(
                tempDir,
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
                """.trimIndent(),
            )
        val parser =
            BinPhpParser().apply {
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
            }

        val result = parser.execute()
        assertEquals(0, result.code)
        val output = result.output.readText()
        assertTrue(output.contains("Stmt_Class"))
        assertTrue(output.contains("Stmt_ClassMethod"))
    }

    @Test
    fun `should use cache on second execution with same command`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val parser =
            BinPhpParser().apply {
                workTmpDir = tempDir
                target = phpFile
                dumpType = BinPhpParser.DumpType.JSON
                doCacheOutput = true
            }

        val r1 = parser.execute()
        assertEquals(0, r1.code)
        val r2 = parser.execute()
        assertEquals(0, r2.code)
        assertEquals(r1.output.absolutePath, r2.output.absolutePath)
    }

    @Test
    fun `should restore config after executeWith`() {
        val phpFile1 = createPhpFile(tempDir, "<?php echo 1;")
        val phpFile2 = createPhpFile(tempDir, "<?php echo 2;")
        val parser =
            BinPhpParser().apply {
                target = phpFile1
                dumpType = BinPhpParser.DumpType.S_EXPR
            }

        val result =
            parser.executeWith {
                target = phpFile2
                dumpType = BinPhpParser.DumpType.JSON
            }
        assertNotNull(result)
        assertEquals(BinPhpParser.DumpType.S_EXPR, parser.dumpType)
    }

    // ========== Recovery Mode (--with-recovery) ==========

    @Test
    fun `recovery should parse broken PHP without crashing`() {
        val phpFile = createPhpFile(tempDir, "<?php function foo( { return 1; }")
        val result =
            BinPhpParser()
                .apply {
                    target = phpFile
                    dumpType = BinPhpParser.DumpType.JSON
                    doWithRecovery = true
                }.execute()
        assertTrue(
            result.output.readText().isNotEmpty(),
            "Recovery mode should produce output even for broken PHP",
        )
    }

    @Test
    fun `recovery should handle unclosed string`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 'unclosed;")
        val result =
            BinPhpParser()
                .apply {
                    target = phpFile
                    dumpType = BinPhpParser.DumpType.JSON
                    doWithRecovery = true
                }.execute()
        assertTrue(result.output.readText().isNotEmpty(), "Recovery should produce output for unclosed string")
    }

    // ========== Edge Cases ==========

    @Test
    fun `should parse empty PHP file`() {
        val phpFile = createPhpFile(tempDir, "<?php")
        val result =
            BinPhpParser()
                .apply {
                    target = phpFile
                    dumpType = BinPhpParser.DumpType.JSON
                }.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `should parse PHP file with only HTML`() {
        val phpFile = createPhpFile(tempDir, "<html><body>Hello</body></html>")
        val result =
            BinPhpParser()
                .apply {
                    target = phpFile
                    dumpType = BinPhpParser.DumpType.JSON
                }.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `should parse PHP 8 features`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                enum Status { case Active; case Inactive; }
                function test(int|string ${'$'}x): void {}
                class Foo { public readonly string ${'$'}name; }
                """.trimIndent(),
            )
        val result =
            BinPhpParser()
                .apply {
                    target = phpFile
                    dumpType = BinPhpParser.DumpType.JSON
                }.execute()
        assertEquals(0, result.code, "Should parse PHP 8 syntax")
        val json = result.output.readText()
        assertTrue(json.contains("Stmt_Enum"), "Should contain enum")
        assertTrue(json.contains("UnionType"), "Should contain union type")
    }
}
