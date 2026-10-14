package edu.jhu.cobra.externs.phpparser

/*
 * Tests for the JSON encoding of the Dump Script — bytes that are not valid UTF-8, deep ASTs and infinite floats.
 *
 * - `invalid UTF-8 byte in a literal should dump as U+FFFD` — raw 0xff inside a string literal
 * - `escaped invalid byte in a literal should dump as U+FFFD` — `"\xff"` escape decoded into the value
 * - `Latin-1 identifier should dump as U+FFFD` — `$caf\xe9` in a Latin-1 file
 * - `valid UTF-8 names should stay distinct` — `$é` and `$e` dump as two names
 * - `offsets should index the unchanged file bytes` — startFilePos/endFilePos after an invalid byte
 * - `dump should equal json_encode output` — compact text of json_encode on a sample file
 * - `deep AST should dump every node` — 5000-term `.` chain, deeper than json_encode reaches
 * - `infinite float literal should dump as 1e999` — `1e999`, which json_encode rejects, as a JSON number
 */

import edu.jhu.cobra.externs.phpparser.binary.BinaryResult
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Chained `.` operations nesting the AST past the about 3250 levels json_encode reaches on an 8 MB stack.
private const val CONCAT_CHAIN_PAST_STACK_LIMIT = 5000

// Sample source and its json_encode text under the same flags, kept as classpath test resources.
private const val SAMPLE_SOURCE = "json_dump_sample.php"
private const val SAMPLE_JSON = "json_dump_sample.json"

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
        assertTrue(json.contains("\"value\":\"\\ufffd\""), "Literal byte 0xff should dump as U+FFFD: $json")
    }

    @Test
    fun `escaped invalid byte in a literal should dump as U+FFFD`() {
        val json = parseJson(createPhpFile(latin1Bytes("<?php echo \"\\xff\";")), resolve = false)
        assertTrue(json.contains("\"value\":\"\\ufffd\""), "Decoded value 0xff should dump as U+FFFD: $json")
    }

    @Test
    fun `Latin-1 identifier should dump as U+FFFD`() {
        val json = parseJson(createPhpFile(latin1Bytes("<?php \$caf\u00e9 = 1;")), resolve = false)
        assertTrue(json.contains("\"name\":\"caf\\ufffd\""), "Latin-1 0xe9 should dump as U+FFFD: $json")
    }

    @Test
    fun `valid UTF-8 names should stay distinct`() {
        val json = parseJson(createPhpFile("<?php \$é = 1; \$e = 2;".toByteArray(Charsets.UTF_8)), resolve = false)
        assertTrue(json.contains("\"name\":\"\\u00e9\""), "UTF-8 é should dump unchanged: $json")
        assertTrue(json.contains("\"name\":\"e\""), "ASCII e should dump as its own name: $json")
    }

    @Test
    fun `offsets should index the unchanged file bytes`() {
        val code = latin1Bytes("<?php \"\u00ff\"; \$x;")
        val json = parseJson(createPhpFile(code), resolve = false)
        val variable =
            Regex(
                """"nodeType":"Expr_Variable","attributes":\{[^}]*"startFilePos":(\d+)[^}]*""" +
                    """"endFilePos":(\d+)[^}]*},"name":"x"""",
            ).find(json)
        assertNotNull(variable, "JSON should hold Expr_Variable x: $json")
        val start = code.indexOf('$'.code.toByte())
        assertEquals(listOf(start, start + 1), variable.groupValues.drop(1).map(String::toInt))
    }

    // The JSON line of a dump; the header lines before it go to the same output.
    private fun jsonLine(output: String): String = output.lineSequence().single { it.startsWith("[") }

    private fun dumpJson(code: String): BinaryResult =
        BinPhpParser()
            .apply {
                target = createPhpFile(code.toByteArray(Charsets.UTF_8))
                dumpType = BinPhpParser.DumpType.JSON
            }.execute()

    @Test
    fun `dump should equal json_encode output`() {
        val source = checkNotNull(javaClass.getResourceAsStream(SAMPLE_SOURCE)).use { it.readBytes() }
        val expected = checkNotNull(javaClass.getResourceAsStream(SAMPLE_JSON)).use { it.readBytes() }
        val json = parseJson(createPhpFile(source), resolve = false)
        assertEquals(String(expected, Charsets.UTF_8), jsonLine(json))
    }

    @Test
    fun `deep AST should dump every node`() {
        val result = dumpJson("<?php \$x = 1" + " . 1".repeat(CONCAT_CHAIN_PAST_STACK_LIMIT) + ";")
        assertEquals(0, result.code, "A deep AST PHP runs should dump")
        val literals = Regex("\"nodeType\":\"Scalar_Int\"").findAll(result.output.readText()).count()
        assertEquals(CONCAT_CHAIN_PAST_STACK_LIMIT + 1, literals)
    }

    @Test
    fun `infinite float literal should dump as 1e999`() {
        val result = dumpJson("<?php \$x = 1e999;")
        assertEquals(0, result.code, "An infinite float literal PHP runs should dump")
        assertTrue(result.output.readText().contains("\"value\":1e999"), "INF should dump as 1e999")
    }
}
