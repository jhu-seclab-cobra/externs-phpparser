package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] binary resolution — bundled extraction, system PATH fallback, platform detection.
 * Resolution helpers are mocked (mockkStatic on the ExecutableSearch/PhpVersionValidation/ArchiveExtraction
 * facades) to force each resolution branch.
 *
 * - `should throw with resource context when parser zip resource missing` — missing classpath zip names the resource
 * - `should reuse existing extraction when checksum already matches` — a target with the expected CRC32
 *   short-circuits extraction entirely, so concurrent JVMs never re-extract a valid binary
 * - `should fall back to system PATH when bundled PHP zip missing` — zip missing fallback
 * - `should fall back to system PATH with valid PHP found` — PATH search
 * - `should throw when bundled zip missing and no system PHP` — no PHP throws
 * - `should throw when system PHP version too low in fallback` — invalid version throws
 * - `should extract bundled PHP from zip when CRC32 mismatch` — extraction on mismatch
 * - `should throw when PHP zip extraction fails` — extraction failure throws
 * - `should throw when parser zip extraction fails` — parser extraction failure throws
 * - `should report os and arch when platform is unsupported` — unknown OS never resolves a
 *   bundled binary; without system PHP it throws with the raw os/arch in the message
 */

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.io.TempDir
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class BinPhpParserResolutionTest {
    @TempDir
    lateinit var tempDir: Path

    // Stubs the resolution helper boundary so every run misses the extraction cache and extraction is a no-op.
    private fun withMockedResolutionHelpers(block: () -> Unit) {
        mockkResolutionHelpers()
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            justRun { extractFileFromZip(any(), any(), *anyVararg()) }
            block()
        } finally {
            unmockkAll()
        }
    }

    // Mocks the top-level facades BinPhpParser resolves binaries through.
    private fun mockkResolutionHelpers() =
        mockkStatic(
            "edu.jhu.cobra.externs.phpparser.ExecutableSearchKt",
            "edu.jhu.cobra.externs.phpparser.PhpVersionValidationKt",
            "edu.jhu.cobra.externs.phpparser.ArchiveExtractionKt",
        )

    // Hides classpath resources whose names start with the prefix for the duration of the block.
    private fun withBlockedResource(
        prefix: String,
        block: () -> Unit,
    ) {
        val origLoader = Thread.currentThread().contextClassLoader
        val blockingLoader =
            object : ClassLoader(origLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith(prefix)) return null
                    return super.getResourceAsStream(name)
                }
            }
        Thread.currentThread().contextClassLoader = blockingLoader
        try {
            block()
        } finally {
            Thread.currentThread().contextClassLoader = origLoader
        }
    }

    @Test
    fun `should throw with resource context when parser zip resource missing`() {
        withMockedResolutionHelpers {
            withBlockedResource("php-parser-") {
                val exception =
                    assertFailsWith<ExternalBinaryNotFoundException> {
                        BinPhpParser()
                    }
                assertTrue(
                    "php-parser-5.7.0.zip" in exception.message.orEmpty(),
                    "message should name the missing resource: ${exception.message}",
                )
            }
        }
    }

    @Test
    fun `should fall back to system PATH when bundled PHP zip missing`() {
        val sysPhp = searchBin("php") ?: return
        withMockedResolutionHelpers {
            every { searchBin(name = any<String>()) } returns sysPhp
            withBlockedResource("php-cli-") {
                val parser = BinPhpParser()
                assertNotNull(parser)
            }
        }
    }

    @Test
    fun `should fall back to system PATH with valid PHP found`() {
        val fakeBin = Files.createTempFile(tempDir, "fake-php", "").toFile()
        fakeBin.writeText("fake-php")
        withMockedResolutionHelpers {
            every { searchBin(name = any<String>()) } returns fakeBin
            withBlockedResource("php-cli-") {
                val parser = BinPhpParser()
                assertNotNull(parser)
            }
        }
    }

    @Test
    fun `should throw when system PHP version too low in fallback`() {
        val fakeBin = Files.createTempFile(tempDir, "fake-php", "").toFile()
        fakeBin.writeText("fake-php")
        withMockedResolutionHelpers {
            every { isPhpVersionValid(any(), any(), any()) } returns false
            every { searchBin(name = any<String>()) } returns fakeBin
            withBlockedResource("php-cli-") {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            }
        }
    }

    @Test
    fun `should throw when bundled zip missing and no system PHP`() {
        withMockedResolutionHelpers {
            every { searchBin(name = any<String>()) } returns null
            withBlockedResource("php-cli-") {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            }
        }
    }

    @Test
    fun `should reuse existing extraction when checksum already matches`() {
        val fakeBin = Files.createTempFile(tempDir, "fake-php", "").toFile()
        mockkResolutionHelpers()
        try {
            // The parser target reports its expected CRC32; both bundled resources are blocked,
            // so construction can only succeed through the checksum short-circuit.
            every { any<Path>().crc32ChecksumString } returns "95f828b5"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { searchBin(name = any<String>()) } returns fakeBin
            withBlockedResource("php-") {
                val parser = BinPhpParser()
                assertNotNull(parser)
            }
            verify(exactly = 0) { extractFileFromZip(any(), any(), *anyVararg()) }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should extract bundled PHP from zip when CRC32 mismatch`() {
        withMockedResolutionHelpers {
            val parser = BinPhpParser()
            assertNotNull(parser)
        }
    }

    @Test
    fun `should throw when PHP zip extraction fails`() {
        withMockedResolutionHelpers {
            every {
                extractFileFromZip(any(), any(), *anyVararg())
            } throws ExternalBinaryNotFoundException("php", "the zip archive")

            assertFailsWith<ExternalBinaryNotFoundException> {
                BinPhpParser()
            }
        }
    }

    @Test
    fun `should throw when parser zip extraction fails`() {
        withMockedResolutionHelpers {
            var callCount = 0
            every { extractFileFromZip(any(), any(), *anyVararg()) } answers {
                callCount++
                if (callCount > 1) throw ExternalBinaryNotFoundException("php-parser.phar", "the zip archive")
            }

            assertFailsWith<ExternalBinaryNotFoundException> {
                BinPhpParser()
            }
        }
    }

    @Test
    fun `should report os and arch when platform is unsupported`() {
        val origOsName = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "solarix")
            withMockedResolutionHelpers {
                every { searchBin(name = any<String>()) } returns null

                val exception =
                    assertFailsWith<ExternalBinaryNotFoundException> {
                        BinPhpParser()
                    }
                assertTrue(
                    exception.message.orEmpty().contains("solarix"),
                    "Exception should name the unsupported OS, got: ${exception.message}",
                )
            }
        } finally {
            System.setProperty("os.name", origOsName)
        }
    }
}
