package edu.jhu.cobra.externs.phpparser

import edu.jhu.cobra.externs.phpparser.abc.AbcBinary
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.div

/**
 * Parses PHP files into ASTs using the php-parser binary.
 *
 * @param phpBinary PHP interpreter binary, or null to auto-detect.
 * @param parserBinary php-parser binary, or null to auto-detect.
 */
class BinPhpParser(phpBinary: File? = null, parserBinary: File? = null) : AbcBinary() {

    /** Output format for parsed AST. */
    enum class DumpType(val opt: String) {
        S_EXPR("--dump"),
        VAR("--var-dump"),
        JSON("--json-dump");

        override fun toString(): String = opt
    }

    private val preloadOsUniformer = mapOf(
        "mac" to "macos", "win" to "windows",
        "nix" to "linux", "nux" to "linux", "aix" to "linux"
    )

    private val preloadArchUniformer = mapOf(
        "aarch64" to "aarch64", "arm64" to "aarch64",
        "x86_64" to "x86_64", "amd64" to "x86_64"
    )

    private val preloadCrc32CheckSum = mapOf(
        "php-cli-8.4-linux-aarch64" to "714a9a7b",
        "php-cli-8.4-linux-x86_64" to "afd3bd14",
        "php-cli-8.4-macos-aarch64" to "7a3d2fca",
        "php-cli-8.4-macos-x86_64" to "3500f339",
        "php-cli-8.4-windows-x86_64" to "ef39e63d",
        "php-parser-5.7.0" to "95f828b5"
    )

    private val phpBinaryFile: File = run {
        if (phpBinary != null && isPhpVersionValid(phpBinary, "7.1")) return@run phpBinary
        val rawOsName = System.getProperty("os.name", "unknown").lowercase()
        val uniOsName = preloadOsUniformer.firstNotNullOfOrNull { (k, v) -> v.takeIf { k in rawOsName } }
        val rawArchName = System.getProperty("os.arch", "unknown").lowercase()
        val uniArchName = preloadArchUniformer.firstNotNullOfOrNull { (k, v) -> v.takeIf { k in rawArchName } }
        val fileName = "php-cli-8.4-${uniOsName}-${uniArchName}".lowercase()
        val expFilePath = this.workTmpDir / fileName // the work tmp dir of the tool located in the tmp dir of sys
        if (expFilePath.crc32ChecksumString == preloadCrc32CheckSum[fileName]) return@run expFilePath.toFile()
        val loadStream = Thread.currentThread().contextClassLoader.getResourceAsStream("$fileName.zip")
        if (loadStream == null) {
            val foundPhpBinary = searchBin("php")?.takeIf { isPhpVersionValid(it, "7.1") }
            return@run foundPhpBinary ?: throw ExternalBinaryNotFoundException("php7.1+", "resources or sys paths")
        }
        val doUnzipSuccess = extractFileFromZip(loadStream, expFilePath, Path("php"), Path("php.exe"))
        if (!doUnzipSuccess) throw ExternalBinaryNotFoundException("php", "unzip failed")
        return@run expFilePath.toFile().apply { setExecutable(true) }
    }

    private val parserBinaryFile: File = run {
        if (parserBinary != null) return@run parserBinary
        val fileName = "php-parser-5.7.0"
        val expFilePath = this.workTmpDir / fileName
        if (expFilePath.crc32ChecksumString == preloadCrc32CheckSum[fileName]) return@run expFilePath.toFile()
        val loadStream = Thread.currentThread().contextClassLoader.getResourceAsStream("$fileName.zip")!!
        val doUnzipSuccess = extractFileFromZip(loadStream, expFilePath, Path("php-parser.phar"))
        if (!doUnzipSuccess) throw ExternalBinaryNotFoundException("php-parser.phar", "unzip failed")
        return@run expFilePath.toFile()
    }


    /**
     * The target PHP file to be parsed.
     */
    var target: File by Argument("entryFile")

    /**
     * The type of dump output to produce, defaults to simple expression output.
     */
    var dumpType: DumpType by Argument("dumpType", DumpType.S_EXPR)

    /** Pretty-print the AST output. */
    var doPrettyPrint: Boolean by Option("--pretty-print", false)

    /** Resolve names in the AST. */
    var doResolveName: Boolean by Option("--resolve-others", false)

    /** Include column information in output. */
    var doWithColInfo: Boolean by Option("--with-column-info", false)

    /** Include position information in output. */
    var doWithPositions: Boolean by Option("--with-positions", false)

    /** Recover from parse errors instead of failing. */
    var doWithRecovery: Boolean by Option("--with-recovery", false)

    override fun getCommandArray(): Array<String> = buildList {
        add(phpBinaryFile.absolutePath)
        add(parserBinaryFile.absolutePath)
        for ((key, value) in allOptions) if (value is Boolean && value) add(key)
        add(dumpType.toString())
        add(target.absolutePath)
    }.toTypedArray()
}