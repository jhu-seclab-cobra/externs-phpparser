package edu.jhu.cobra.externs.phpparser

import edu.jhu.cobra.externs.phpparser.abc.AbcBinary
import edu.jhu.cobra.externs.phpparser.abc.BinaryResult
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

private val PHP_VERSION_OUTPUT_REGEX = Regex("""PHP (\d+\.\d+\.\d+)""")
private val VERSION_FORMAT_REGEX = Regex("""^\d+(\.\d+){0,2}$""")

// Liveness backstop for a wedged interpreter during the version probe.
private const val VERSION_PROBE_TIMEOUT_SECONDS = 10L

// Dotted version strings compare over at most major.minor.patch components.
private const val VERSION_COMPONENT_COUNT = 3

// Streaming buffer for checksum reads; balances syscall count against allocation size.
private const val CRC_BUFFER_SIZE = 16384

/**
 * Executes with temporary configuration that is rolled back after completion.
 *
 * @param tmpConfig configuration applied before execution.
 * @return the execution result.
 */
public fun <T : AbcBinary> T.executeWith(tmpConfig: T.() -> Unit): BinaryResult {
    val argsBackup = HashMap(allArguments)
    val optionsBackup = HashMap(allOptions)
    try {
        tmpConfig(this)
        return execute()
    } finally {
        allArguments.clear()
        allArguments.putAll(argsBackup)
        allOptions.clear()
        allOptions.putAll(optionsBackup)
    }
}

/**
 * Looks up an executable file as a direct child of a directory.
 *
 * @param under The directory whose direct children are checked.
 * @param possibleNames Vararg of candidate file names.
 * @return The first candidate that is an executable regular file; null when none is.
 */
public fun searchBin(
    under: Path,
    vararg possibleNames: String,
): File? =
    possibleNames
        .asSequence()
        .map { name -> File(under.toFile(), name) }
        .firstOrNull { file -> file.isFile && file.canExecute() }

/**
 * Searches for an executable by name in the system PATH.
 *
 * @return the first matching executable; null when none is found or the PATH variable is unset.
 */
public fun searchBin(name: String): File? {
    val osName = System.getProperty("os.name").lowercase()
    val isWinBin = osName.contains("win") && !(name.endsWith(".exe") || name.endsWith(".bat"))
    val exeNames = if (isWinBin) arrayOf("$name.exe", "$name.bat") else arrayOf(name)
    val sysPath = System.getenv("PATH") ?: return null
    return sysPath
        .splitToSequence(File.pathSeparator)
        .map { Path(it) }
        .filter { it.exists() }
        .mapNotNull { path -> searchBin(path, *exeNames) }
        .firstOrNull()
}

// Runs `binary -v` and returns its first output line; a hung probe is force-killed and reported.
private fun probeVersionLine(binary: File): String? {
    val process = ProcessBuilder(binary.absolutePath, "-v").start()
    if (!process.waitFor(VERSION_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly().waitFor()
        throw ExternalBinaryInvalidException(
            binary.absolutePath,
            "version probe timed out after $VERSION_PROBE_TIMEOUT_SECONDS s",
        )
    }
    return process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLine() }
}

/**
 * Reads the version reported by `php -v`.
 *
 * @throws ExternalBinaryInvalidException when the binary cannot run, hangs past the probe timeout,
 * or its output carries no version.
 */
private fun readPhpVersion(binary: File): String {
    val output =
        try {
            probeVersionLine(binary)
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
    val currentParts = current.split(".").map { it.toInt() }
    val requiredParts = minRequired.split(".").map { it.toInt() }
    // Compare versions
    for (i in 0..<VERSION_COMPONENT_COUNT) {
        val curPart = currentParts.getOrElse(i) { 0 }
        val reqPart = requiredParts.getOrElse(i) { 0 }
        if (curPart > reqPart) return true
        if (curPart < reqPart) return false
    }
    // All parts are equal
    return includeEqual
}

/**
 * Extracts the first ZIP entry matching one of [fromZipPath] to [toOutPath].
 *
 * @throws ExternalBinaryNotFoundException when no entry matches any of [fromZipPath].
 */
public fun extractFileFromZip(
    zipInputStream: InputStream,
    toOutPath: Path,
    vararg fromZipPath: Path,
) {
    toOutPath.createParentDirectories() // Create parent directories for the output file if they don't exist

    fun String.uniform() = replace(oldChar = '\\', newChar = '/')
    ZipInputStream(zipInputStream).use { zip ->
        val uniTargets = fromZipPath.map { it.toString().uniform() }
        var inZipEntry: ZipEntry?
        do {
            inZipEntry = zip.nextEntry
        } while (inZipEntry != null && inZipEntry.name.uniform() !in uniTargets)
        if (inZipEntry == null) throw ExternalBinaryNotFoundException(uniTargets.joinToString(), "the zip archive")
        Files.copy(zip, toOutPath, REPLACE_EXISTING)
    }
}

/** CRC32 checksum as lowercase hex string, or null if the file does not exist. */
public val Path.crc32ChecksumString: String?
    get() {
        // Validate file exists and is a regular file
        if (!exists() || !isRegularFile()) return null
        val crc = CRC32()
        // Use NIO for platform-independent binary reading
        inputStream().use { inputStream ->
            var bytesRead: Int
            val buffer = ByteArray(CRC_BUFFER_SIZE)
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        // Format as 8-character lowercase hex string with leading zeros
        return "%08x".format(crc.value)
    }

/** CRC32 checksum as lowercase hex string, or null if the file does not exist. */
public val File.crc32ChecksumString: String? get() = toPath().crc32ChecksumString
