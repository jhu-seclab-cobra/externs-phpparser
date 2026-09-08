package edu.jhu.cobra.externs.phpparser

/*
 * Tests for PHP version validation in PhpVersionValidation.kt — probe, parse, and comparison.
 *
 * - `isPhpVersionValid should return true when current version is higher` — higher version passes.
 * - `isPhpVersionValid should return true when versions are equal and includeEqual is true` — equal passes with flag.
 * - `isPhpVersionValid should return false when versions are equal and includeEqual is false` — equal fails without flag.
 * - `isPhpVersionValid should return false when current version is lower` — lower version fails.
 * - `isPhpVersionValid should handle incomplete version numbers` — 1- and 2-part versions compared correctly.
 * - `isPhpVersionValid should throw on invalid minRequired format` — invalid format throws.
 * - `isPhpVersionValid should throw when binary produces no version output` — non-version output throws.
 * - `isPhpVersionValid should throw when binary does not exist` — missing binary throws.
 * - `isPhpVersionValid should attach IOException cause when binary cannot run` — process-start failure keeps cause.
 * - `readPhpVersion should report timeout when version probe hangs` — a hung probe throws a distinct
 *   "version probe timed out" reason instead of misreporting unparsable output; the test shortens the
 *   backstop through the internal probe parameter.
 * - `isPhpVersionValid should compare major version correctly` — major-only comparison.
 * - `isPhpVersionValid should compare minor version when major is equal` — minor comparison.
 * - `isPhpVersionValid should compare patch version when major and minor are equal` — patch comparison.
 * - `isPhpVersionValid should handle single-digit minRequired` — single-component version.
 * - `isPhpVersionValid should throw when binary outputs empty` — empty output throws.
 * - `isPhpVersionValid should handle two-part minRequired against three-part current` — mixed lengths.
 * - `isPhpVersionValid should throw on malformed minRequired` — empty, four-component, and
 *   empty-component strings all fall outside the documented dotted-version format.
 * - `readPhpVersion should parse real-world php -v lines` — suffixed versions (RC, distro build
 *   metadata) still yield the numeric triple.
 * - `isPhpVersionValid should throw on minRequired component beyond Int range` — an oversized
 *   component is a format error per design.md, not a NumberFormatException.
 */

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PhpVersionValidationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `isPhpVersionValid should return true when current version is higher`() {
        val mock = createMockPhpBinary("8.2.5")
        assertTrue(isPhpVersionValid(mock, "7.1"))
        assertTrue(isPhpVersionValid(mock, "8.2.4"))
    }

    @Test
    fun `isPhpVersionValid should return true when versions are equal and includeEqual is true`() {
        val mock = createMockPhpBinary("7.4.10")
        assertTrue(isPhpVersionValid(mock, "7.4.10", includeEqual = true))
    }

    @Test
    fun `isPhpVersionValid should return false when versions are equal and includeEqual is false`() {
        val mock = createMockPhpBinary("7.4.10")
        assertFalse(isPhpVersionValid(mock, "7.4.10", includeEqual = false))
    }

    @Test
    fun `isPhpVersionValid should return false when current version is lower`() {
        val mock = createMockPhpBinary("7.4.10")
        assertFalse(isPhpVersionValid(mock, "8.0"))
        assertFalse(isPhpVersionValid(mock, "7.5"))
        assertFalse(isPhpVersionValid(mock, "7.4.11"))
    }

    @Test
    fun `isPhpVersionValid should handle incomplete version numbers`() {
        val mock = createMockPhpBinary("8.1.0")
        assertTrue(isPhpVersionValid(mock, "8"))
        assertTrue(isPhpVersionValid(mock, "8.1"))
        assertFalse(isPhpVersionValid(mock, "8.2"))
    }

    @Test
    fun `isPhpVersionValid should throw on invalid minRequired format`() {
        val mock = createMockPhpBinary("8.0.0")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "invalid.version")
        }
    }

    @Test
    fun `isPhpVersionValid should throw when binary produces no version output`() {
        val mock = createMockScript("mock-php-bad.sh", "#!/bin/sh\necho 'not a version'")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should throw when binary does not exist`() {
        val fake = File("/tmp/nonexistent-php-binary-xyz")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(fake, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should attach IOException cause when binary cannot run`() {
        val fake = File("/tmp/nonexistent-php-binary-xyz")
        val exception =
            assertFailsWith<ExternalBinaryInvalidException> {
                isPhpVersionValid(fake, "7.1")
            }
        assertTrue(exception.cause is IOException, "expected IOException cause, got: ${exception.cause}")
    }

    @Test
    fun `readPhpVersion should report timeout when version probe hangs`() {
        // Sleeps well past the shortened probe backstop; the probe must kill it and name the timeout.
        val mock = createMockScript("mock-php-hang.sh", "#!/bin/sh\nsleep 30\necho 'PHP 8.0.0 (cli)'")
        val exception =
            assertFailsWith<ExternalBinaryInvalidException> {
                readPhpVersion(mock, probeTimeoutSeconds = 1)
            }
        assertTrue(
            "version probe timed out" in exception.message.orEmpty(),
            "hung probe must report a timeout, not unparsable output: ${exception.message}",
        )
    }

    @Test
    fun `isPhpVersionValid should compare major version correctly`() {
        val mock = createMockPhpBinary("8.0.0")
        assertTrue(isPhpVersionValid(mock, "7.0.0"))
        assertFalse(isPhpVersionValid(mock, "9.0.0"))
    }

    @Test
    fun `isPhpVersionValid should compare minor version when major is equal`() {
        val mock = createMockPhpBinary("8.2.0")
        assertTrue(isPhpVersionValid(mock, "8.1.0"))
        assertFalse(isPhpVersionValid(mock, "8.3.0"))
    }

    @Test
    fun `isPhpVersionValid should compare patch version when major and minor are equal`() {
        val mock = createMockPhpBinary("8.2.5")
        assertTrue(isPhpVersionValid(mock, "8.2.4"))
        assertFalse(isPhpVersionValid(mock, "8.2.6"))
        assertTrue(isPhpVersionValid(mock, "8.2.5", includeEqual = true))
        assertFalse(isPhpVersionValid(mock, "8.2.5", includeEqual = false))
    }

    @Test
    fun `isPhpVersionValid should handle single-digit minRequired`() {
        val mock = createMockPhpBinary("8.0.0")
        assertTrue(isPhpVersionValid(mock, "7"))
        assertTrue(isPhpVersionValid(mock, "8"))
        assertFalse(isPhpVersionValid(mock, "9"))
    }

    @Test
    fun `isPhpVersionValid should throw when binary outputs empty`() {
        val mock = createMockScript("mock-php-empty.sh", "#!/bin/sh\n")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "7.1")
        }
    }

    @Test
    fun `isPhpVersionValid should handle two-part minRequired against three-part current`() {
        val mock = createMockPhpBinary("8.2.0")
        assertTrue(isPhpVersionValid(mock, "8.2"))
        assertFalse(isPhpVersionValid(mock, "8.2", includeEqual = false))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1.2.3.4", "1..2", "1.2."])
    fun `isPhpVersionValid should throw on malformed minRequired`(minRequired: String) {
        val mock = createMockPhpBinary("8.0.0")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, minRequired)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "PHP 8.3.0RC1 (cli), 8.3.0",
        "PHP 8.1.2-1ubuntu2.14 (cli) (built: Aug 18 2023 11:41:11) (NTS), 8.1.2",
    )
    fun `readPhpVersion should parse real-world php -v lines`(
        line: String,
        expected: String,
    ) {
        val mock = createMockScript("mock-php-line-${line.hashCode()}.sh", "#!/bin/sh\necho '$line'")
        assertEquals(expected, readPhpVersion(mock))
    }

    @Test
    fun `isPhpVersionValid should throw on minRequired component beyond Int range`() {
        val mock = createMockPhpBinary("8.0.0")
        assertFailsWith<ExternalBinaryInvalidException> {
            isPhpVersionValid(mock, "9999999999")
        }
    }

    // --- helpers ---

    private fun createMockPhpBinary(version: String): File =
        createMockScript(
            "mock-php-$version.sh",
            "#!/bin/sh\necho 'PHP $version (cli) (built: Jan 1 2024 00:00:00) (NTS)'",
        )

    private fun createMockScript(
        name: String,
        script: String,
    ): File =
        tempDir.resolve(name).toFile().apply {
            writeText(script)
            setExecutable(true)
        }
}
