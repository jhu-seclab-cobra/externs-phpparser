package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [executeWith] — temporary configuration scoped to one execution.
 *
 * - `should restore config after executeWith` — arguments restored after executeWith.
 * - `should use temporary config during executeWith` — temporary config used during execution.
 * - `executeWith restores state when execute throws` — snapshot restored on exception.
 */

import edu.jhu.cobra.externs.phpparser.binary.AbcBinary
import edu.jhu.cobra.externs.phpparser.binary.BinaryResult
import edu.jhu.cobra.externs.phpparser.binary.EchoBinary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class ScopedExecutionTest {
    @Test
    fun `should restore config after executeWith`() {
        val binary = EchoBinary()
        binary.message = "original"

        val result =
            binary.executeWith {
                message = "temporary"
            }
        assertNotNull(result)
        assertEquals("original", binary.message)
    }

    @Test
    fun `should use temporary config during executeWith`() {
        val binary = EchoBinary()
        binary.message = "original"
        binary.verbose = false

        val result =
            binary.executeWith {
                message = "temp-msg"
                verbose = true
            }
        assertEquals(0, result.code)
        assertTrue(result.output.readText().contains("temp-msg"))
        assertEquals(false, binary.verbose)
    }

    @Test
    fun `executeWith restores state when execute throws`() {
        val binary =
            object : AbcBinary() {
                var arg: String by Argument<String>("arg")
                var opt: Boolean by Option("--flag", false)

                override fun getCommandArray(): Array<String> = arrayOf("echo")

                override fun execute(): BinaryResult = error("boom")
            }
        binary.arg = "original"
        binary.opt = false

        assertFailsWith<IllegalStateException> {
            binary.executeWith {
                arg = "temporary"
                opt = true
            }
        }
        assertEquals("original", binary.arg)
        assertEquals(false, binary.opt)
    }
}
