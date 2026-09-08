package edu.jhu.cobra.externs.phpparser.binary

/*
 * Tests for [AbcBinary.execute] — process spawning, caching, and the liveness backstop.
 *
 * - `should execute and return success result` — execute returns code 0 with output file.
 * - `should return cached output on repeated execution` — second execute returns same cached file.
 * - `should keep distinct cache entries for hash-colliding commands` — 32-bit contentHashCode collision
 *   must not replay the wrong cached output.
 * - `should not replay failed run from cache` — a failed run is never cached as success.
 * - `should return code -1 on timeout` — a process outliving the backstop returns code -1.
 * - `should reap TERM-ignoring process before returning on timeout` — destroy escalates to destroyForcibly
 *   so no process survives past the timeout return.
 * - `timeout output should render duration in milliseconds` — no ISO-8601 duration in the timeout message.
 * - `timeout output should stay inside workTmpDir` — no orphan temp file outside the working directory.
 * - `should complete within backstop without timing out` — a process finishing before the backstop succeeds.
 * - `should skip creating workTmpDir if it already exists` — no error on existing dir.
 * - `concurrent executions of the same command should not share an output file` — each run stages
 *   its own stdout file, so identical concurrent commands never clobber each other.
 * - `interrupted wait should reap the process and preserve the interrupt flag` — an interrupt during
 *   the wait kills the spawned process, restores the thread's interrupt flag, and rethrows.
 * - `should propagate IOException when the executable does not exist` — spawn failure surfaces as
 *   the OS-level exception, not a BinaryResult.
 * - `arguments with spaces and unicode should pass through unmodified` — array-based spawning does
 *   no shell re-quoting.
 * - `cache keys should distinguish arguments differing only in whitespace` — quoting-sensitive
 *   commands never collide on one cache entry.
 */

import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

internal class AbcBinaryExecutionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should execute and return success result`() {
        val binary = EchoBinary()
        binary.message = "hello"
        val result = binary.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.exists())
        assertTrue(result.output.readText().contains("hello"))
    }

    @Test
    fun `should return cached output on repeated execution`() {
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.message = "cache-test"
        binary.doCacheOutput = true

        val result1 = binary.execute()
        assertEquals(0, result1.code)

        val result2 = binary.execute()
        assertEquals(0, result2.code)
        assertEquals(result1.output.absolutePath, result2.output.absolutePath)
    }

    @Test
    fun `should keep distinct cache entries for hash-colliding commands`() {
        // "Aa" and "BB" share a String.hashCode, so the command arrays collide on contentHashCode.
        assertEquals(arrayOf("echo", "Aa").contentHashCode(), arrayOf("echo", "BB").contentHashCode())
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.doCacheOutput = true

        binary.message = "Aa"
        val result1 = binary.execute()
        assertEquals(0, result1.code)

        binary.message = "BB"
        val result2 = binary.execute()
        assertEquals(0, result2.code)
        assertNotEquals(result1.output.absolutePath, result2.output.absolutePath)
        assertTrue(
            result2.output.readText().contains("BB"),
            "colliding command must not replay the other command's cached output",
        )
    }

    @Test
    fun `should not replay failed run from cache`() {
        val binary = FailBinary()
        binary.workTmpDir = tempDir
        binary.doCacheOutput = true

        val result1 = binary.execute()
        assertTrue(result1.code != 0)

        val result2 = binary.execute()
        assertTrue(result2.code != 0)
    }

    @Test
    fun `should return code -1 on timeout`() {
        val binary = SleepBinary(executionTimeoutMillis = 0)
        binary.workTmpDir = tempDir

        val result = binary.execute()
        assertEquals(-1, result.code)
        assertTrue(result.output.exists())
    }

    @Test
    fun `should reap TERM-ignoring process before returning on timeout`() {
        val marker = "cobra-abc-reap-${UUID.randomUUID()}"
        val binary = StubbornBinary(marker, executionTimeoutMillis = 200, terminationGraceMillis = 200)
        binary.workTmpDir = tempDir

        val result = binary.execute()
        val survivor = ProcessBuilder("pgrep", "-f", marker).start()
        survivor.waitFor()
        val stillAlive = survivor.exitValue() == 0
        // Reap any survivor so a failure does not leak a 60s process into the environment.
        ProcessBuilder("pkill", "-9", "-f", marker).start().waitFor()
        assertEquals(-1, result.code)
        assertFalse(stillAlive, "process ignoring SIGTERM must be force-killed before execute returns")
    }

    @Test
    fun `timeout output should render duration in milliseconds`() {
        val binary = SleepBinary(executionTimeoutMillis = 0)
        binary.workTmpDir = tempDir

        val result = binary.execute()
        val message = result.output.readText()
        assertTrue("timed out after 0 ms" in message, "message should render milliseconds, got: $message")
    }

    @Test
    fun `timeout output should stay inside workTmpDir`() {
        val binary = SleepBinary(executionTimeoutMillis = 0)
        binary.workTmpDir = tempDir

        val result = binary.execute()
        assertEquals(
            binary.workTmpDir.toFile().absolutePath,
            result.output.parentFile.absolutePath,
            "timeout output must not leave an orphan file outside workTmpDir",
        )
    }

    @Test
    fun `should complete within backstop without timing out`() {
        val binary = SleepBinary(seconds = 1, executionTimeoutMillis = 30_000)
        binary.workTmpDir = tempDir

        val result = binary.execute()
        assertEquals(0, result.code)
    }

    @Test
    fun `concurrent executions of the same command should not share an output file`() {
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.message = "concurrent"

        val results = ConcurrentLinkedQueue<BinaryResult>()
        val workers = (1..4).map { thread { results.add(binary.execute()) } }
        workers.forEach { it.join() }

        assertEquals(4, results.size)
        results.forEach { result ->
            assertEquals(0, result.code)
            assertTrue(result.output.readText().contains("concurrent"))
        }
        assertEquals(
            4,
            results.map { it.output.absolutePath }.toSet().size,
            "identical concurrent commands must each write their own output file",
        )
    }

    @Test
    fun `interrupted wait should reap the process and preserve the interrupt flag`() {
        val marker = "cobra-abc-interrupt-${UUID.randomUUID()}"
        val binary = StubbornBinary(marker, executionTimeoutMillis = 60_000, terminationGraceMillis = 200)
        binary.workTmpDir = tempDir

        var thrown: Throwable? = null
        var flagPreserved = false
        val worker =
            thread {
                try {
                    binary.execute()
                } catch (expected: InterruptedException) {
                    thrown = expected
                    flagPreserved = Thread.currentThread().isInterrupted
                }
            }
        awaitProcessWithMarker(marker)
        worker.interrupt()
        worker.join()

        val survivor = ProcessBuilder("pgrep", "-f", marker).start()
        survivor.waitFor()
        val stillAlive = survivor.exitValue() == 0
        // Reap any survivor so a failure does not leak a 60s process into the environment.
        ProcessBuilder("pkill", "-9", "-f", marker).start().waitFor()
        assertTrue(thrown is InterruptedException, "execute must rethrow the interrupt, got: $thrown")
        assertTrue(flagPreserved, "the thread's interrupt flag must be restored before rethrowing")
        assertFalse(stillAlive, "the spawned process must not outlive an interrupted wait")
    }

    // Bounded poll until the marker process is running, so the interrupt lands during the wait.
    private fun awaitProcessWithMarker(marker: String) {
        repeat(50) {
            val probe = ProcessBuilder("pgrep", "-f", marker).start()
            probe.waitFor()
            if (probe.exitValue() == 0) return
            Thread.sleep(100)
        }
        error("process with marker $marker never appeared")
    }

    @Test
    fun `should propagate IOException when the executable does not exist`() {
        val binary = MissingBinary()
        binary.workTmpDir = tempDir
        assertFailsWith<IOException> { binary.execute() }
    }

    @Test
    fun `arguments with spaces and unicode should pass through unmodified`() {
        val message = """hello world "quoted" 中文 ${'$'};(0)[1]{2}"""
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.message = message

        val result = binary.execute()
        assertEquals(0, result.code)
        assertTrue(
            result.output.readText().contains(message),
            "array-based spawning must not re-quote or split the argument",
        )
    }

    @Test
    fun `cache keys should distinguish arguments differing only in whitespace`() {
        val binary = EchoBinary()
        binary.workTmpDir = tempDir
        binary.doCacheOutput = true

        binary.message = "a b"
        val result1 = binary.execute()
        binary.message = "a  b"
        val result2 = binary.execute()

        assertEquals(0, result1.code)
        assertEquals(0, result2.code)
        assertNotEquals(
            result1.output.absolutePath,
            result2.output.absolutePath,
            "whitespace-only differences must produce distinct cache entries",
        )
    }

    @Test
    fun `should skip creating workTmpDir if it already exists`() {
        val binary = EchoBinary()
        binary.message = "dir-exists"
        val r1 = binary.execute()
        assertEquals(0, r1.code)
        val r2 = binary.execute()
        assertEquals(0, r2.code)
    }
}
