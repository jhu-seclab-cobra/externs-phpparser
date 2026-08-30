package edu.jhu.cobra.externs.phpparser

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile

// Streaming buffer for checksum reads; balances syscall count against allocation size.
private const val CRC_BUFFER_SIZE = 16384

/**
 * Extracts the first ZIP entry matching one of [fromZipPath] to [toOutPath].
 *
 * The entry is staged as a unique sibling file and moved into place atomically, so concurrent
 * readers — including other JVMs extracting the same archive — only ever observe an absent or
 * complete [toOutPath], never a partially written one.
 *
 * @throws ExternalBinaryNotFoundException when no entry matches any of [fromZipPath].
 */
public fun extractFileFromZip(
    zipInputStream: InputStream,
    toOutPath: Path,
    vararg fromZipPath: Path,
) {
    toOutPath.createParentDirectories()

    fun String.uniform() = replace(oldChar = '\\', newChar = '/')
    ZipInputStream(zipInputStream).use { zip ->
        val uniTargets = fromZipPath.map { it.toString().uniform() }
        var inZipEntry: ZipEntry?
        do {
            inZipEntry = zip.nextEntry
        } while (inZipEntry != null && inZipEntry.name.uniform() !in uniTargets)
        if (inZipEntry == null) throw ExternalBinaryNotFoundException(uniTargets.joinToString(), "the zip archive")
        stageAndPublish(zip, toOutPath)
    }
}

// Writes the entry stream to a unique sibling then renames it over the target in one step.
private fun stageAndPublish(
    entryStream: InputStream,
    toOutPath: Path,
) {
    val stagePath = createTempFile(toOutPath.parent, ".${toOutPath.fileName}", ".part")
    try {
        Files.copy(entryStream, stagePath, REPLACE_EXISTING)
        try {
            Files.move(stagePath, toOutPath, ATOMIC_MOVE)
        } catch (ignored: AtomicMoveNotSupportedException) {
            // Same-directory rename without atomic support: plain replace is the closest guarantee.
            Files.move(stagePath, toOutPath, REPLACE_EXISTING)
        }
    } finally {
        stagePath.deleteIfExists()
    }
}

/** CRC32 checksum as lowercase hex string, or null if the file does not exist. */
public val Path.crc32ChecksumString: String?
    get() {
        if (!exists() || !isRegularFile()) return null
        val crc = CRC32()
        inputStream().use { inputStream ->
            var bytesRead: Int
            val buffer = ByteArray(CRC_BUFFER_SIZE)
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                crc.update(buffer, 0, bytesRead)
            }
        }
        return "%08x".format(crc.value)
    }

/** CRC32 checksum as lowercase hex string, or null if the file does not exist. */
public val File.crc32ChecksumString: String? get() = toPath().crc32ChecksumString
