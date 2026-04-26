package edu.jhu.cobra.externs.phpparser.abc

/**
 * Tests for [BinaryResult] — immutable data holder for execution outcomes.
 *
 * - `should store code and output file` — constructor stores code and output.
 * - `should support data class equality` — equal fields produce equal instances.
 * - `should distinguish different codes` — different codes produce unequal instances.
 * - `should support copy with modified code` — copy() creates modified instance.
 */

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BinaryResultTest {

    @Test
    fun `should store code and output file`() {
        val file = File("/tmp/output.txt")
        val result = BinaryResult(0, file)
        assertEquals(0, result.code)
        assertEquals(file, result.output)
    }

    @Test
    fun `should support data class equality`() {
        val file = File("/tmp/out.txt")
        val r1 = BinaryResult(0, file)
        val r2 = BinaryResult(0, file)
        assertEquals(r1, r2)
    }

    @Test
    fun `should distinguish different codes`() {
        val file = File("/tmp/out.txt")
        assertNotEquals(BinaryResult(0, file), BinaryResult(1, file))
    }

    @Test
    fun `should support copy with modified code`() {
        val file = File("/tmp/out.txt")
        val original = BinaryResult(0, file)
        val copied = original.copy(code = -1)
        assertEquals(-1, copied.code)
        assertEquals(file, copied.output)
    }
}
