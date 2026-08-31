package edu.jhu.cobra.externs.phpparser

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

private val PHP_VERSION_OUTPUT_REGEX = Regex("""PHP (\d+\.\d+\.\d+)""")
private val VERSION_FORMAT_REGEX = Regex("""^\d+(\.\d+){0,2}$""")

// Liveness backstop for a wedged interpreter during the version probe.
internal const val VERSION_PROBE_TIMEOUT_SECONDS = 10L

// Dotted version strings compare over at most major.minor.patch components.
private const val VERSION_COMPONENT_COUNT = 3

// A component that overflows Int is outside the documented dotted-version format.
private fun parseVersionParts(version: String): List<Int> =
    version.split(".").map { component ->
        component.toIntOrNull()
            ?: throw ExternalBinaryInvalidException(version, "version component out of range: $component")
    }

// Runs `binary -v` and returns its first output line; a hung probe is force-killed and reported.
private fun probeVersionLine(
    binary: File,
    probeTimeoutSeconds: Long,
): String? {
    val process = ProcessBuilder(binary.absolutePath, "-v").start()
    if (!process.waitFor(probeTimeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly().waitFor()
        throw ExternalBinaryInvalidException(
            binary.absolutePath,
            "version probe timed out after $probeTimeoutSeconds s",
        )
    }
    return process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLine() }
}

/**
 * Reads the version reported by `php -v`.
 *
 * Internal so tests can shorten the probe backstop; production callers use the default.
 *
 * @throws ExternalBinaryInvalidException when the binary cannot run, hangs past the probe timeout,
 * or its output carries no version.
 */
internal fun readPhpVersion(
    binary: File,
    probeTimeoutSeconds: Long = VERSION_PROBE_TIMEOUT_SECONDS,
): String {
    val output =
        try {
            probeVersionLine(binary, probeTimeoutSeconds)
        } catch (cause: IOException) {
            throw ExternalBinaryInvalidException(binary.absolutePath, "could not run version probe", cause)
        }
    // Extract version from output like "PHP 7.4.10 (cli) ..."
    val matchResult = output?.let { PHP_VERSION_OUTPUT_REGEX.find(it) }
    return matchResult?.groupValues?.get(1)
        ?: throw ExternalBinaryInvalidException(binary.absolutePath, "unparsable version output: ${output.orEmpty()}")
}

/**
 * Validates that the PHP binary version meets [minRequired].
 *
 * @param includeEqual true for >=, false for strict >
 * @throws ExternalBinaryInvalidException when the binary cannot run, reports no parsable version,
 * or [minRequired] is not a dotted version string.
 */
public fun isPhpVersionValid(
    binary: File,
    minRequired: String,
    includeEqual: Boolean = true,
): Boolean {
    if (!VERSION_FORMAT_REGEX.matches(minRequired)) {
        throw ExternalBinaryInvalidException(minRequired, "invalid version format")
    }
    val current = readPhpVersion(binary)
    val currentParts = parseVersionParts(current)
    val requiredParts = parseVersionParts(minRequired)
    for (i in 0..<VERSION_COMPONENT_COUNT) {
        val curPart = currentParts.getOrElse(i) { 0 }
        val reqPart = requiredParts.getOrElse(i) { 0 }
        if (curPart > reqPart) return true
        if (curPart < reqPart) return false
    }
    return includeEqual
}
