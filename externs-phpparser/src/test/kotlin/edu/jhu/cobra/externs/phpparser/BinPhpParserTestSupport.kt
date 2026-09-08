package edu.jhu.cobra.externs.phpparser

/*
 * Shared helpers for the BinPhpParser test files.
 *
 * - `createPhpFile` — writes PHP source into a caller-owned temp directory.
 * - `parseJson` — runs the parser with JSON dump on a file and returns the raw JSON text.
 */

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

internal fun createPhpFile(
    dir: Path,
    code: String,
): File = Files.createTempFile(dir, "test", ".php").toFile().apply { writeText(code) }

internal fun parseJson(
    phpFile: File,
    resolve: Boolean,
): String =
    BinPhpParser()
        .apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doResolveName = resolve
        }.execute()
        .also { assertEquals(0, it.code, "Parse should succeed") }
        .output
        .readText()
