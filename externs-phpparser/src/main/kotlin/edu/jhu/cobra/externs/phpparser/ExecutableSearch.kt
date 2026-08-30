package edu.jhu.cobra.externs.phpparser

import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

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
    val osName = System.getProperty("os.name", "unknown").lowercase()
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
