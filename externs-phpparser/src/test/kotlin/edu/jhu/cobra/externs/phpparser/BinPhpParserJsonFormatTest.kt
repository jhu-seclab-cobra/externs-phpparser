package edu.jhu.cobra.externs.phpparser

/*
 * Tests for [BinPhpParser] JSON output format — node types, attributes, positions, and scalar encoding.
 *
 * - `positions should include startFilePos and endFilePos in JSON` — position fields
 * - `JSON output should contain nodeType for all nodes` — nodeType present
 * - `JSON output should contain attributes with line numbers` — line numbers
 * - `JSON should encode all PHP statement types` — class/method/if/return/etc
 * - `JSON should encode expression types` — binary ops/cast/ternary/array
 * - `JSON should encode v5 scalar types correctly` — Scalar_Int/Float/String
 * - `JSON should encode modifier flags correctly` — flags field
 */

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

internal class BinPhpParserJsonFormatTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `positions should include startFilePos and endFilePos in JSON`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val json =
            BinPhpParser()
                .apply {
                    target = phpFile
                    dumpType = BinPhpParser.DumpType.JSON
                    doWithPositions = true
                }.execute()
                .output
                .readText()
        assertTrue(json.contains("startFilePos"), "Should contain startFilePos")
        assertTrue(json.contains("endFilePos"), "Should contain endFilePos")
    }

    @Test
    fun `JSON output should contain nodeType for all nodes`() {
        val phpFile = createPhpFile(tempDir, "<?php \$x = 1; echo \$x;")
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("\"nodeType\""), "JSON should contain nodeType field")
        assertTrue(json.contains("Stmt_Expression"), "Should contain expression statement")
    }

    @Test
    fun `JSON output should contain attributes with line numbers`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("\"startLine\""), "Should contain startLine")
        assertTrue(json.contains("\"endLine\""), "Should contain endLine")
    }

    @Test
    fun `JSON should encode all PHP statement types`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                use App\Foo;
                class Bar extends Foo {
                    const X = 1;
                    public string ${'$'}name;
                    public function test(int ${'$'}a): string {
                        if (${'$'}a > 0) { return "pos"; }
                        else { return "neg"; }
                    }
                }
                function helper() {}
                interface IFace {}
                trait MyTrait {}
                enum Color { case Red; case Blue; }
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("Stmt_Class"), "Should contain class")
        assertTrue(json.contains("Stmt_ClassMethod"), "Should contain method")
        assertTrue(json.contains("Stmt_Property"), "Should contain property")
        assertTrue(json.contains("Stmt_ClassConst"), "Should contain class constant")
        assertTrue(json.contains("Stmt_Function"), "Should contain function")
        assertTrue(json.contains("Stmt_Interface"), "Should contain interface")
        assertTrue(json.contains("Stmt_Trait"), "Should contain trait")
        assertTrue(json.contains("Stmt_Enum"), "Should contain enum")
        assertTrue(json.contains("Stmt_If"), "Should contain if")
        assertTrue(json.contains("Stmt_Return"), "Should contain return")
    }

    @Test
    fun `JSON should encode expression types`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                ${'$'}x = 1 + 2;
                ${'$'}y = "hello" . " world";
                ${'$'}z = [1, 2, 3];
                ${'$'}w = (int)"42";
                ${'$'}v = ${'$'}x > 0 ? "yes" : "no";
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("Expr_BinaryOp_Plus") || json.contains("BinaryOp_Plus"), "Should contain addition")
        assertTrue(json.contains("Expr_BinaryOp_Concat") || json.contains("BinaryOp_Concat"), "Should contain concat")
        assertTrue(json.contains("Expr_Array"), "Should contain array literal")
        assertTrue(json.contains("Expr_Cast_Int"), "Should contain int cast")
        assertTrue(json.contains("Expr_Ternary"), "Should contain ternary")
    }

    @Test
    fun `JSON should encode v5 scalar types correctly`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                ${'$'}i = 42;
                ${'$'}f = 3.14;
                ${'$'}s = "hello";
                ${'$'}b = true;
                ${'$'}n = null;
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("Scalar_Int"), "Int should be Scalar_Int (v5 rename from LNumber)")
        assertTrue(json.contains("Scalar_Float"), "Float should be Scalar_Float (v5 rename from DNumber)")
        assertTrue(json.contains("Scalar_String"), "Should contain Scalar_String")
    }

    @Test
    fun `JSON should encode modifier flags correctly`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                class Foo {
                    public int ${'$'}a;
                    protected string ${'$'}b;
                    private float ${'$'}c;
                    public static function bar() {}
                    final public function baz() {}
                }
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("\"flags\""), "Should contain flags field for modifiers")
    }
}
