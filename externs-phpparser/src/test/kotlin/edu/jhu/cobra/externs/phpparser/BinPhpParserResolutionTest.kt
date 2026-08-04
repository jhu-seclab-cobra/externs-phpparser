package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] binary resolution — bundled extraction, system PATH fallback, platform detection.
 * Utils functions are mocked (mockkStatic on UtilsKt) to force each resolution branch.
 *
 * - `should throw with resource context when parser zip resource missing` — missing classpath zip names the resource
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

    // Stubs the Utils boundary so every run misses the extraction cache and extraction is a no-op.
    private fun withMockedUtils(block: () -> Unit) {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            justRun { extractFileFromZip(any(), any(), *anyVararg()) }
            block()
        } finally {
            unmockkAll()
        }
    }

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
        withMockedUtils {
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
        withMockedUtils {
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
        withMockedUtils {
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
        withMockedUtils {
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
        withMockedUtils {
            every { searchBin(name = any<String>()) } returns null
            withBlockedResource("php-cli-") {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            }
        }
    }

    @Test
    fun `should extract bundled PHP from zip when CRC32 mismatch`() {
        withMockedUtils {
            val parser = BinPhpParser()
            assertNotNull(parser)
        }
    }

    @Test
    fun `should throw when PHP zip extraction fails`() {
        withMockedUtils {
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
        withMockedUtils {
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
            withMockedUtils {
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
