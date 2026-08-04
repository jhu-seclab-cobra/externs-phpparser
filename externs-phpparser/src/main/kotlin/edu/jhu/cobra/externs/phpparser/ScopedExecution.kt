package edu.jhu.cobra.externs.phpparser

import edu.jhu.cobra.externs.phpparser.binary.AbcBinary
import edu.jhu.cobra.externs.phpparser.binary.BinaryResult

/**
 * Executes with temporary configuration that is rolled back after completion.
 *
 * @param tmpConfig configuration applied before execution.
 * @return the execution result.
 */
public fun <T : AbcBinary> T.executeWith(tmpConfig: T.() -> Unit): BinaryResult =
    withConfigurationSnapshot {
        tmpConfig()
        execute()
    }
