package edu.jhu.cobra.externs.phpparser.abc

import java.io.File

/** Result of a binary execution: exit code and combined stdout/stderr output file. */
data class BinaryResult(val code: Int, val output: File)


