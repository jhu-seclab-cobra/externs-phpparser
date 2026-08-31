package edu.jhu.cobra.externs.phpparser

/**
 * Tests for BinPhpParser failure paths — malformed input, missing targets, and the output header contract.
 *
 * - `should fail on broken PHP without recovery` — a syntax error without --with-recovery exits non-zero.
 * - `should fail on unexpected end of file` — a source cut mid-statement exits non-zero.
 * - `should fail on unterminated comment` — an open block comment exits non-zero.
 * - `should fail on nonexistent target file` — a missing target exits non-zero.
 * - `should fail on null byte in source` — a NUL character in the source exits non-zero.
 * - `should parse source with a leading BOM` — a UTF-8 BOM before the open tag still parses.
 * - `output should start with the file header line` — impl.md contract: every parse output is
 *   prefixed with a `====> File` header that downstream consumers must skip.
 */

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class BinPhpParserFailureTest {
    @TempDir
    lateinit var tempDir: Path

    private fun parse(code: String): Int {
        val phpFile = createPhpFile(tempDir, code)
        val parser = BinPhpParser()
        parser.target = phpFile
        return parser.execute().code
    }

    @Test
    fun `should fail on broken PHP without recovery`() {
        assertNotEquals(0, parse("<?php function () { }"), "a syntax error without recovery must exit non-zero")
    }

    @Test
    fun `should fail on unexpected end of file`() {
        assertNotEquals(0, parse("<?php foo("), "a source cut mid-statement must exit non-zero")
    }

    @Test
    fun `should fail on unterminated comment`() {
        assertNotEquals(0, parse("<?php /*"), "an open block comment must exit non-zero")
    }

    @Test
    fun `should fail on nonexistent target file`() {
        val parser = BinPhpParser()
        parser.target = tempDir.resolve("does-not-exist.php").toFile()
        assertNotEquals(0, parser.execute().code, "a missing target must exit non-zero")
    }

    @Test
    fun `should fail on null byte in source`() {
        assertNotEquals(0, parse("<?php echo \u0000;"), "a NUL character in the source must exit non-zero")
    }

    @Test
    fun `should parse source with a leading BOM`() {
        assertEquals(0, parse("\uFEFF<?php echo 1;"), "a UTF-8 BOM before the open tag must still parse")
    }

    @Test
    fun `output should start with the file header line`() {
        val phpFile = createPhpFile(tempDir, "<?php echo 1;")
        val output = parseJson(phpFile, resolve = false)
        assertTrue(
            output.lineSequence().first().startsWith("====> File"),
            "downstream consumers skip the documented header; got: ${output.lineSequence().first()}",
        )
    }
}
