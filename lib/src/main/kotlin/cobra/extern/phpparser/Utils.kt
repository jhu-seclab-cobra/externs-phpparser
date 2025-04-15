package cobra.extern.phpparser

import cobra.extern.phpparser.abc.AbcBinary
import cobra.extern.phpparser.abc.BinaryResult
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

/**
 * Temporarily modifies the configuration of this executable, executes it, and then restores the original configuration.
 * This function allows for temporary adjustments to an executable's arguments or options just for the duration of one execution.
 *
 * @param T The specific type of the executable.
 * @param tmpConfig A lambda function to configure temporary changes to the executable.
 * @return [BinaryResult] containing the exit code and output file from the execution.
 * @throws ExecutableMissException if the executable command cannot be executed due to missing file or permissions.
 */
fun <T : AbcBinary> T.executeWith(tmpConfig: T.() -> Unit): BinaryResult {
    val argsBackup = allArguments.toMap()
    val optionsBackup = allOptions.toMap()
    this.tmpConfig()
    val output = execute()
    allArguments.putAll(argsBackup)
    allOptions.putAll(optionsBackup)
    return output
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

/**
 * Searches for an executable file within the system's PATH environment variable directories.
 * Adjusts the executable name for Windows systems by appending '.exe' or '.bat' if necessary.
 *
 * @param name The base name of the executable to search for, without an extension.
 * @return A [File] that represents the executable, or null if it cannot be found.
 */
fun searchBin(name: String): File? {
    val osName = System.getProperty("os.name").lowercase()
    val isWinBin = osName.contains("win") && !(name.endsWith(".exe") || name.endsWith(".bat"))
    val exeNames = if (isWinBin) arrayOf("$name.exe", "$name.bat") else arrayOf(name)
    val sysPath = runCatching { System.getenv("PATH") }.getOrNull() ?: return null
    val envStrPaths = sysPath.split(File.pathSeparator).map { Path(it) }
    val envPaths = envStrPaths.filter { it.exists() }.asSequence()
    return envPaths.mapNotNull { path -> searchBin(path, *exeNames) }.firstOrNull()
}

/**
 * Checks if the current PHP version meets the minimum version requirement.
 * Supports incomplete version numbers as input.
 *
 * @param binary the binary file of PHP executable
 * @param minRequired Minimum required version, can be complete or incomplete format
 * @param includeEqual Whether to include equality case, true means "greater than or equal to", false means "strictly greater than"
 * @return Whether current version meets the minimum version requirement
 */
fun isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean = true): Boolean {
    val current = runCatching {
        val process = ProcessBuilder(binary.absolutePath, "-v").start()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
        val output = process.inputStream.bufferedReader().readLine() ?: return@runCatching null
        // Extract version from output like "PHP 7.4.10 (cli) ..."
        val versionRegex = Regex("""PHP (\d+\.\d+\.\d+)""")
        val matchResult = versionRegex.find(output) ?: return@runCatching null
        matchResult.groupValues[1]
    }.getOrNull() ?: ""
    // Validate version format (accepts "number" or "number.number" or "number.number.number")
    val versionRegex = Regex("""^\d+(\.\d+){0,2}$""")
    if (!versionRegex.matches(current)) throw ExternalBinaryInvalidException("Invalid version format: $current")
    if (!versionRegex.matches(minRequired)) throw ExternalBinaryInvalidException("Invalid version format: $minRequired")
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


/**
 * Extracts a specific file from a ZIP archive to a destination path.
 *
 * @param zipFile The ZIP file to extract from
 * @param fromZipPath The path of the file inside the ZIP to extract
 * @param toOutPath The destination path where the extracted file will be saved
 * @return Boolean indicating whether extraction was successful
 * @throws Exception If any error occurs during extraction
 */
fun extractFileFromZip(zipInputStream: InputStream, toOutPath: Path, vararg fromZipPath: Path): Boolean {
    toOutPath.createParentDirectories() // Create parent directories for the output file if they don't exist
    fun String.uniform() = replace(oldChar = '\\', newChar = '/')
    ZipInputStream(zipInputStream).use { zip ->
        val uniTargets = fromZipPath.map { it.toString().uniform() }
        var inZipEntry: ZipEntry?
        do {
            inZipEntry = zip.nextEntry
            println("Checking entry: ${inZipEntry?.name}")
        } while (inZipEntry != null && inZipEntry.name.uniform() !in uniTargets)
        if (inZipEntry == null) return false // there is no target file existing in the zip file
        Files.copy(zip, toOutPath, REPLACE_EXISTING)
        return true
    }
}

/**
 * Calculates a CRC32 checksum of a file for integrity verification.
 * The implementation is platform-independent, ensuring consistent results
 * across different operating systems for the same file content.
 *
 * @return A hexadecimal string representation of the CRC32 checksum
 * @throws IllegalArgumentException If the file doesn't exist or isn't a regular file
 * @throws Exception If an error occurs while reading the file
 */
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

val File.crc32ChecksumString get() = toPath().crc32ChecksumString




