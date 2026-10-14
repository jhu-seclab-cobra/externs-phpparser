package edu.jhu.cobra.externs.phpparser

/*
 * Tests for the JSON encoding of the Dump Script — bytes that are not valid UTF-8 and encoding failures.
 *
 * - `invalid UTF-8 byte in a literal should dump as U+FFFD` — raw 0xff inside a string literal
 * - `escaped invalid byte in a literal should dump as U+FFFD` — `"\xff"` escape decoded into the value
 * - `Latin-1 identifier should dump as U+FFFD` — `$caf\xe9` in a Latin-1 file
 * - `valid UTF-8 names should stay distinct` — `$é` and `$e` dump as two names
 * - `offsets should index the unchanged file bytes` — startFilePos/endFilePos after an invalid byte
 * - `encoding failure should exit non-zero` — AST deeper than the json_encode depth limit
 */

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Chained `.` operations nesting the AST past the json_encode default depth of 512.
private const val CONCAT_CHAIN_PAST_DEPTH_LIMIT = 600

internal class BinPhpParserEncodingTest {
    @TempDir
    lateinit var tempDir: Path

    private fun createPhpFile(bytes: ByteArray): File =
        Files.createTempFile(tempDir, "encoding", ".php").toFile().apply { writeBytes(bytes) }

    // PHP source text with each `\u00XX` char written as the single byte 0xXX.
    private fun latin1Bytes(code: String): ByteArray = code.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `invalid UTF-8 byte in a literal should dump as U+FFFD`() {
        val json = parseJson(createPhpFile(latin1Bytes("<?php echo \"\u00ff\";")), resolve = false)
        assertTrue(json.contains("\"value\": \"\\ufffd\""), "Literal byte 0xff should dump as U+FFFD: $json")
    }

    @Test
    fun `escaped invalid byte in a literal should dump as U+FFFD`() {
        val json = parseJson(createPhpFile(latin1Bytes("<?php echo \"\\xff\";")), resolve = false)
        assertTrue(json.contains("\"value\": \"\\ufffd\""), "Decoded value 0xff should dump as U+FFFD: $json")
    }

    @Test
    fun `Latin-1 identifier should dump as U+FFFD`() {
        val json = parseJson(createPhpFile(latin1Bytes("<?php \$caf\u00e9 = 1;")), resolve = false)
        assertTrue(json.contains("\"name\": \"caf\\ufffd\""), "Latin-1 0xe9 should dump as U+FFFD: $json")
    }

    @Test
    fun `valid UTF-8 names should stay distinct`() {
        val json = parseJson(createPhpFile("<?php \$é = 1; \$e = 2;".toByteArray(Charsets.UTF_8)), resolve = false)
        assertTrue(json.contains("\"name\": \"\\u00e9\""), "UTF-8 é should dump unchanged: $json")
        assertTrue(json.contains("\"name\": \"e\""), "ASCII e should dump as its own name: $json")
    }

    @Test
    fun `offsets should index the unchanged file bytes`() {
        val code = latin1Bytes("<?php \"\u00ff\"; \$x;")
        val json = parseJson(createPhpFile(code), resolve = false)
        val variable =
            Regex(
                """"nodeType": "Expr_Variable",\s*"attributes": \{[^}]*"startFilePos": (\d+)[^}]*""" +
                    """"endFilePos": (\d+)[^}]*},\s*"name": "x"""",
            ).find(json)
        assertNotNull(variable, "JSON should hold Expr_Variable x: $json")
        val start = code.indexOf('$'.code.toByte())
        assertEquals(listOf(start, start + 1), variable.groupValues.drop(1).map(String::toInt))
    }

    @Test
    fun `encoding failure should exit non-zero`() {
        val code = "<?php \$x = 1" + " . 1".repeat(CONCAT_CHAIN_PAST_DEPTH_LIMIT) + ";"
        val result =
            BinPhpParser()
                .apply {
                    target = createPhpFile(code.toByteArray(Charsets.UTF_8))
                    dumpType = BinPhpParser.DumpType.JSON
                }.execute()
        assertNotEquals(0, result.code, "A failed encoding should not report success")
        assertTrue(result.output.readText().contains("Maximum stack depth exceeded"))
    }
}
