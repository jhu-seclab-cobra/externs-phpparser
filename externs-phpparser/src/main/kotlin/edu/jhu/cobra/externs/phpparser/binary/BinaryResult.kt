package edu.jhu.cobra.externs.phpparser.binary

import java.io.File

/** Result of a binary execution: exit code and combined stdout/stderr output file. */
public data class BinaryResult(
    public val code: Int,
    public val output: File,
)
