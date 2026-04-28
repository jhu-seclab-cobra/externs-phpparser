package edu.jhu.cobra.externs.phpparser

/**
 * Microbenchmarks for hot-path utility functions — measures throughput and compares alternatives.
 *
 * - `P1-1 benchmark extractFileFromZip` — ZIP extraction throughput (1K ops).
 * - `P1-2 benchmark regex compilation pattern` — cached vs per-call regex compilation.
 * - `P1-3 benchmark cache key hashing` — joinToString-hashCode vs contentHashCode.
 * - `P1-4 benchmark PATH split pattern` — eager split+filter vs lazy splitToSequence.
 * - `P1-5 benchmark command array building` — filterValues+spread vs buildList single-pass.
 */

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.math.absoluteValue

@Tag("performance")
class PerformanceBenchmark {

    companion object {
        private const val WARMUP_ITERATIONS = 5
        private const val MEASUREMENT_ITERATIONS = 7
        private const val OPS_PER_ITERATION = 10_000
    }

    data class BenchmarkResult(val name: String, val nsPerOp: Double, val opsPerSec: Double)

    private fun benchmark(name: String, opsPerIteration: Int = OPS_PER_ITERATION, block: () -> Unit): BenchmarkResult {
        repeat(WARMUP_ITERATIONS) { repeat(opsPerIteration) { block() } }
        val times = mutableListOf<Long>()
        repeat(MEASUREMENT_ITERATIONS) {
            val start = System.nanoTime()
            repeat(opsPerIteration) { block() }
            val elapsed = System.nanoTime() - start
            times.add(elapsed)
        }
        val medianNs = times.sorted()[times.size / 2]
        val avgNsPerOp = medianNs.toDouble() / opsPerIteration
        val opsPerSec = 1_000_000_000.0 / avgNsPerOp
        println("[$name] median: ${medianNs / 1_000_000}ms / $opsPerIteration ops, ${"%.1f".format(avgNsPerOp)} ns/op, ${"%.0f".format(opsPerSec)} ops/s")
        return BenchmarkResult(name, avgNsPerOp, opsPerSec)
    }

    @Test
    fun `P1-1 benchmark extractFileFromZip`() {
        val zipBytes = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                repeat(20) { i ->
                    zos.putNextEntry(ZipEntry("entry-$i.txt"))
                    zos.write("content $i".toByteArray())
                    zos.closeEntry()
                }
                zos.putNextEntry(ZipEntry("target.txt"))
                zos.write("target content".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        val tempOutput = Files.createTempFile("perf-output", ".txt")
        benchmark("extractFileFromZip", opsPerIteration = 1_000) {
            extractFileFromZip(zipBytes.inputStream(), tempOutput, Path("target.txt"))
        }
        tempOutput.toFile().delete()
    }

    @Test
    fun `P1-2 benchmark regex compilation pattern`() {
        val pattern1 = """PHP (\d+\.\d+\.\d+)"""
        val pattern2 = """^\d+(\.\d+){0,2}$"""
        val testInput1 = "PHP 7.4.10 (cli) (built: Oct 2020)"
        val testInput2 = "7.4.10"
        benchmark("regex-new-every-call") {
            val r1 = Regex(pattern1)
            r1.find(testInput1)
            val r2 = Regex(pattern2)
            r2.matches(testInput2)
            r2.matches("7.4")
        }
        val cachedR1 = Regex(pattern1)
        val cachedR2 = Regex(pattern2)
        benchmark("regex-cached") {
            cachedR1.find(testInput1)
            cachedR2.matches(testInput2)
            cachedR2.matches("7.4")
        }
    }

    @Test
    fun `P1-3 benchmark cache key hashing`() {
        val cmdArray = arrayOf(
            "/usr/local/bin/php", "/tmp/cobra/binaries/BinPhpParser/php-parser-5.7.0",
            "--json-dump", "--with-positions", "/path/to/some/file.php"
        )
        benchmark("joinToString-hashCode") {
            cmdArray.joinToString(" ").hashCode().absoluteValue.toString()
        }
        benchmark("contentHashCode") {
            cmdArray.contentHashCode().absoluteValue.toString()
        }
    }

    @Test
    fun `P1-4 benchmark PATH split pattern`() {
        val sysPath = System.getenv("PATH") ?: "/usr/bin:/usr/local/bin"
        benchmark("split-map-filter-list") {
            sysPath.split(File.pathSeparator).map { Path(it) }
                .filter { Files.exists(it) }.firstOrNull()
        }
        benchmark("splitToSequence-lazy") {
            sysPath.splitToSequence(File.pathSeparator)
                .map { Path(it) }
                .filter { Files.exists(it) }
                .firstOrNull()
        }
    }

    @Test
    fun `P1-5 benchmark command array building`() {
        val options = mutableMapOf<String, Any>(
            "--pretty-print" to false,
            "--resolve-names" to false,
            "--with-column-info" to false,
            "--with-positions" to true,
            "--with-recovery" to false
        )
        val phpPath = "/usr/local/bin/php"
        val parserPath = "/tmp/parser.phar"
        val dumpType = "--json-dump"
        val targetPath = "/path/to/file.php"
        benchmark("filterValues-map-spread") {
            val boolOptions = options.filterValues { it is Boolean && it }.map { it.key }.toTypedArray()
            arrayOf(phpPath, parserPath, *boolOptions, dumpType, targetPath)
        }
        benchmark("buildList-single-pass") {
            buildList {
                add(phpPath)
                add(parserPath)
                for ((key, value) in options) if (value is Boolean && value) add(key)
                add(dumpType)
                add(targetPath)
            }.toTypedArray()
        }
    }
}
