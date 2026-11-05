package edu.jhu.cobra.externs.phpparser.binary

/*
 * Tests for [AbcBinary] configuration — backstops, cache default and working directory.
 *
 * - `should initialize with default backstops and cache settings` — verifies backstop constants and cache (off).
 * - `workTmpDir should default under java io tmpdir by class name` — `{tmpdir}/cobra/binaries/{className}`.
 */

import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AbcBinaryConfigurationTest {
    @Test
    fun `should initialize with default backstops and cache settings`() {
        val binary = EchoBinary()
        assertEquals(EXECUTION_TIMEOUT_MILLIS, binary.executionTimeoutMillis)
        assertEquals(TERMINATION_GRACE_MILLIS, binary.terminationGraceMillis)
        assertEquals(false, binary.doCacheOutput)
    }

    @Test
    fun `workTmpDir should default under java io tmpdir by class name`() {
        val expected = Path(System.getProperty("java.io.tmpdir")) / "cobra" / "binaries" / "EchoBinary"
        assertEquals(expected, EchoBinary().workTmpDir)
    }
}
