/*
 * Readiness failures in the real stubborn-process fixture.
 * - `should reap the owned process when readiness expires` — a six-second shell startup reports
 *   readiness failure and leaves no live owned PID after execute exits exceptionally.
 */
package edu.jhu.cobra.externs.phpparser.binary

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Isolated
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Isolated
internal class AbcBinaryReadinessTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should reap the owned process when readiness expires`() {
        val readyFile = tempDir.resolve("ready.pid")
        val wrapperPid = tempDir.resolve("wrapper.pid")
        val searchPath = delayedShellPath()
        val binary =
            object : StubbornBinary(readyFile, timeoutMillis = 2_000, terminationGraceMillis = 200) {
                override fun getCommandArray(): Array<String> = arrayOf("/usr/bin/env", "PATH=$searchPath") + super.getCommandArray()
            }
        binary.workTmpDir = tempDir

        try {
            val failure = assertFailsWith<IllegalStateException> { binary.execute() }
            val pid = wrapperPid.toFile().readText(Charsets.UTF_8)
            assertTrue(failure.message.orEmpty().contains("process did not publish a live PID"))
            assertFalse(isProcessAlive(pid), "readiness failure must reap the started process before execute throws")
        } finally {
            cleanupWrapper(wrapperPid)
        }
    }

    private fun delayedShellPath(): String {
        val directory = Files.createDirectory(tempDir.resolve("slow-sh"))
        val shell = directory.resolve("sh").toFile()
        shell.writeText(
            "#!/bin/sh\nprintf '%s' \"\$\$\" > wrapper.pid\n/bin/sleep 6\nexec /bin/sh \"\$@\"\n",
            Charsets.UTF_8,
        )
        check(shell.setExecutable(true))
        return "$directory${File.pathSeparator}${System.getenv("PATH")}"
    }

    private fun cleanupWrapper(pidFile: Path) {
        if (!Files.exists(pidFile)) return
        val pid = pidFile.toFile().readText(Charsets.UTF_8)
        if (isProcessAlive(pid)) ProcessBuilder("kill", "-9", pid).start().waitFor()
    }
}
