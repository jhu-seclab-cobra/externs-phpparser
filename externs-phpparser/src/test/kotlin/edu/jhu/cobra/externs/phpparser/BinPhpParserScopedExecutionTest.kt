package edu.jhu.cobra.externs.phpparser

/*
 * Tests for [BinPhpParser.executeWith] — temporary configuration scoped to one execution.
 *
 * - `executeWith applies the temporary config and restores every argument and flag` — normal completion.
 * - `executeWith restores the config when the configuration lambda throws` — exception before execution.
 * - `executeWith restores the config when execute throws and keeps workTmpDir and doCacheOutput` —
 *   only arguments and options roll back.
 * - `executeWith restores the config and reaps the child when the wait is interrupted` — interrupt path.
 */

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class BinPhpParserScopedExecutionTest {
    @TempDir
    lateinit var tempDir: Path

    private data class Config(
        val target: File,
        val dumpType: BinPhpParser.DumpType,
        val flags: List<Boolean>,
    )

    private fun BinPhpParser.config() =
        Config(target, dumpType, listOf(doPrettyPrint, doResolveName, doWithColInfo, doWithPositions, doWithRecovery))

    private fun configuredParser(
        php: File? = null,
        source: File = createPhpFile(tempDir, "<?php echo 1;"),
    ) = BinPhpParser(php).apply {
        workTmpDir = tempDir
        target = source
        dumpType = BinPhpParser.DumpType.VAR
        doPrettyPrint = true
        doWithPositions = true
    }

    private fun BinPhpParser.flipEverything() {
        target = createPhpFile(tempDir, "<?php echo 2;")
        dumpType = BinPhpParser.DumpType.JSON
        doPrettyPrint = false
        doResolveName = true
        doWithColInfo = true
        doWithPositions = false
        doWithRecovery = true
    }

    @Test
    fun `executeWith applies the temporary config and restores every argument and flag`() {
        val parser = configuredParser()
        val before = parser.config()
        var during: Config? = null

        val result =
            parser.executeWith {
                flipEverything()
                during = config()
            }

        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
        assertEquals(BinPhpParser.DumpType.JSON, during?.dumpType)
        assertEquals(listOf(false, true, true, false, true), during?.flags)
        assertEquals(before, parser.config())
    }

    @Test
    fun `executeWith restores the config when the configuration lambda throws`() {
        val parser = configuredParser()
        val before = parser.config()

        assertFailsWith<IllegalStateException> {
            parser.executeWith {
                flipEverything()
                error("boom")
            }
        }
        assertEquals(before, parser.config())
    }

    @Test
    fun `executeWith restores the config when execute throws and keeps workTmpDir and doCacheOutput`() {
        val parser = configuredParser()
        val before = parser.config()
        val blocker = Files.createFile(tempDir.resolve("not-a-directory"))
        val unusable = blocker.resolve("work")

        assertFailsWith<java.io.IOException> {
            parser.executeWith {
                flipEverything()
                workTmpDir = unusable
                doCacheOutput = true
            }
        }
        assertEquals(before, parser.config())
        assertEquals(unusable, parser.workTmpDir)
        assertTrue(parser.doCacheOutput)
    }

    @Test
    fun `executeWith restores the config and reaps the child when the wait is interrupted`() {
        val pidFile = tempDir.resolve("child.pid")
        val php = tempDir.resolve("php").toFile()
        php.writeText(
            "#!/bin/sh\n" +
                "if [ \"\$1\" = \"-v\" ]; then echo 'PHP 8.4.0 (cli)'; exit 0; fi\n" +
                "echo \$\$ > '$pidFile.tmp'; mv '$pidFile.tmp' '$pidFile'\n" +
                "exec sleep 600\n",
        )
        check(php.setExecutable(true))
        val parser = configuredParser(php)
        val before = parser.config()
        var thrown: Throwable? = null
        val worker =
            thread {
                try {
                    parser.executeWith { flipEverything() }
                } catch (expected: InterruptedException) {
                    thrown = expected
                }
            }
        val pid = awaitPid(pidFile)
        worker.interrupt()
        worker.join()

        assertTrue(thrown is InterruptedException, "the interrupt must propagate, got: $thrown")
        assertEquals(before, parser.config())
        assertTrue(!isAlive(pid), "the spawned child must not outlive the interrupted wait")
    }

    private fun awaitPid(pidFile: Path): String {
        repeat(WAIT_ATTEMPTS) {
            if (Files.exists(pidFile)) return pidFile.toFile().readText().trim()
            Thread.sleep(WAIT_INTERVAL_MILLIS)
        }
        error("child did not publish its PID at $pidFile")
    }

    private fun isAlive(pid: String): Boolean = ProcessBuilder("kill", "-0", pid).start().waitFor() == 0

    private companion object {
        const val WAIT_ATTEMPTS = 100
        const val WAIT_INTERVAL_MILLIS = 100L
    }
}
