package edu.jhu.cobra.externs.phpparser

import edu.jhu.cobra.externs.phpparser.abc.AbcBinary
import edu.jhu.cobra.externs.phpparser.abc.BinaryResult
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.*

private val PHP_VERSION_OUTPUT_REGEX = Regex("""PHP (\d+\.\d+\.\d+)""")
private val VERSION_FORMAT_REGEX = Regex("""^\d+(\.\d+){0,2}$""")

/**
 * Executes with temporary configuration that is rolled back after completion.
 *
 * @param tmpConfig configuration applied before execution.
 * @return the execution result.
 */
fun <T : AbcBinary> T.executeWith(tmpConfig: T.() -> Unit): BinaryResult {
    val argsBackup = HashMap(allArguments)
    val optionsBackup = HashMap(allOptions)
    try {
        tmpConfig(this)
        return execute()
    } finally {
        allArguments.clear(); allArguments.putAll(argsBackup)
        allOptions.clear(); allOptions.putAll(optionsBackup)
    }
}

/**
 * Searches for a file with a specified name under a given directory and its subdirectories.
 *
 * @param under The root directory from which the search should begin.
 * @param possibleNames Vararg of possible filenames to search for.
 * @return A [File] object representing the first matching file found; null if no file matches.
 */
fun searchBin(under: Path, vararg possibleNames: String): File? =
    under.toFile().walkTopDown().firstOrNull { file -> file.isFile && file.name in possibleNames }

/** Searches for an executable by name in the system PATH. */
fun searchBin(name: String): File? {
    val osName = System.getProperty("os.name").lowercase()
    val isWinBin = osName.contains("win") && !(name.endsWith(".exe") || name.endsWith(".bat"))
    val exeNames = if (isWinBin) arrayOf("$name.exe", "$name.bat") else arrayOf(name)
    val sysPath = runCatching { System.getenv("PATH") }.getOrNull() ?: return null
    return sysPath.splitToSequence(File.pathSeparator)
        .map { Path(it) }
        .filter { it.exists() }
        .mapNotNull { path -> searchBin(path, *exeNames) }
        .firstOrNull()
}

/**
 * Validates that the PHP binary version meets [minRequired].
 *
 * @param includeEqual true for >=, false for strict >
 */
fun isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean = true): Boolean {
    val current = runCatching {
        val process = ProcessBuilder(binary.absolutePath, "-v").start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
        val output = process.inputStream.bufferedReader().readLine() ?: return@runCatching null
        // Extract version from output like "PHP 7.4.10 (cli) ..."
        val matchResult = PHP_VERSION_OUTPUT_REGEX.find(output) ?: return@runCatching null
        matchResult.groupValues[1]
    }.getOrNull() ?: ""
    if (!VERSION_FORMAT_REGEX.matches(current)) throw ExternalBinaryInvalidException("Invalid version format: $current")
    if (!VERSION_FORMAT_REGEX.matches(minRequired)) throw ExternalBinaryInvalidException("Invalid version format: $minRequired")
    val currentParts = current.split(".").map { it.toInt() }
    val requiredParts = minRequired.split(".").map { it.toInt() }
    // Compare versions
    for (i in 0..2) {
        val curPart = currentParts.getOrElse(i) { 0 }
        val reqPart = requiredParts.getOrElse(i) { 0 }
        if (curPart > reqPart) return true
        if (curPart < reqPart) return false
    }
    // All parts are equal
    return includeEqual
}

/** Extracts a file from a ZIP archive to [toOutPath]. */
fun extractFileFromZip(zipInputStream: InputStream, toOutPath: Path, vararg fromZipPath: Path): Boolean {
    toOutPath.createParentDirectories() // Create parent directories for the output file if they don't exist
    fun String.uniform() = replace(oldChar = '\\', newChar = '/')
    ZipInputStream(zipInputStream).use { zip ->
        val uniTargets = fromZipPath.map { it.toString().uniform() }
        var inZipEntry: ZipEntry?
        do {
            inZipEntry = zip.nextEntry
        } while (inZipEntry != null && inZipEntry.name.uniform() !in uniTargets)
        if (inZipEntry == null) return false // there is no target file existing in the zip file
        Files.copy(zip, toOutPath, REPLACE_EXISTING)
        return true
    }
}

/** CRC32 checksum as lowercase hex string, or null if the file does not exist. */
val Path.crc32ChecksumString
    get(): String? {
        // Validate file exists and is a regular file
        if (!exists() || !isRegularFile()) return null
        val crc = CRC32()
        // Use NIO for platform-independent binary reading
        inputStream().use { inputStream ->
            var bytesRead: Int // Use a reasonably sized buffer for efficient reading
            val buffer = ByteArray(16384) // 16KB buffer
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        // Format as 8-character lowercase hex string with leading zeros
        return "%08x".format(crc.value)
    }

/** CRC32 checksum as lowercase hex string, or null if the file does not exist. */
val File.crc32ChecksumString get() = toPath().crc32ChecksumString
