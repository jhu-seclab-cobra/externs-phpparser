/*
 * Concurrent parser instances own binary selection, options and output files.
 * - `concurrent directory searches keep their own binary` — same name in two directories stays local.
 * - `concurrent parsers keep distinct binaries settings and outputs` — real copied interpreters and
 *   PHARs keep independent name resolution and produce separate output files.
 */
package edu.jhu.cobra.externs.phpparser

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class BinPhpParserConcurrencyTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `concurrent directory searches keep their own binary`() {
        val directories = listOf("first", "second").map { Files.createDirectory(tempDir.resolve(it)) }
        val binaries =
            directories.map { directory ->
                Files.write(directory.resolve("php"), byteArrayOf()).toFile().apply { check(setExecutable(true)) }
            }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val searches =
                directories.map { directory ->
                    pool.submit(
                        Callable {
                            check(start.await(WAIT_SECONDS, TimeUnit.SECONDS))
                            List(LOOKUPS) { searchBin(directory, "php") }
                        },
                    )
                }
            start.countDown()
            searches.forEachIndexed { index, search ->
                assertEquals(List(LOOKUPS) { binaries[index] }, search.get(WAIT_SECONDS, TimeUnit.SECONDS))
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent parsers keep distinct binaries settings and outputs`() {
        val source = createPhpFile(tempDir, "<?php namespace App; use Lib\\Thing; new Thing();")
        val seed = BinPhpParser().apply { target = source }.getCommandArray()
        val parsers =
            listOf("first", "second").mapIndexed { index, name ->
                val directory = Files.createDirectory(tempDir.resolve(name))
                val php = Files.copy(Paths.get(seed[0]), directory.resolve("php")).toFile()
                check(php.setExecutable(true))
                val phar = Files.copy(Paths.get(seed[2]), directory.resolve("parser.phar")).toFile()
                BinPhpParser(php, phar).apply {
                    target = source
                    dumpType = BinPhpParser.DumpType.JSON
                    doResolveName = index == 1
                }
            }
        val commands = parsers.map { it.getCommandArray().toList() }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val executions =
                parsers.map { parser ->
                    pool.submit(
                        Callable {
                            check(start.await(WAIT_SECONDS, TimeUnit.SECONDS))
                            parser.executeWith { target = source }
                        },
                    )
                }
            start.countDown()
            val results = executions.map { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
            assertEquals(listOf(0, 0), results.map { it.code })
            assertNotEquals(results[0].output, results[1].output)
            assertFalse("Name_FullyQualified" in results[0].output.readText(Charsets.UTF_8))
            assertTrue("Name_FullyQualified" in results[1].output.readText(Charsets.UTF_8))
            assertEquals(commands, parsers.map { it.getCommandArray().toList() })
            assertEquals(listOf(false, true), parsers.map { it.doResolveName })
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        const val WAIT_SECONDS = 30L
        const val LOOKUPS = 100
    }
}
