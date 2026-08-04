package edu.jhu.cobra.externs.phpparser

import edu.jhu.cobra.externs.phpparser.abc.AbcBinary
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.div

// Lowest interpreter version able to run the bundled php-parser PHAR.
private const val MIN_PHP_VERSION = "7.1"

// Interpreter release bundled as classpath zip resources.
private const val PHP_CLI_VERSION = "8.4"

// php-parser PHAR release bundled as a classpath zip resource.
private const val PARSER_VERSION = "5.7.0"

/**
 * Parses PHP files into ASTs using the php-parser binary.
 *
 * @param phpBinary PHP interpreter binary, or null to auto-detect.
 * @param parserBinary php-parser binary, or null to auto-detect.
 */
public class BinPhpParser(
    phpBinary: File? = null,
    parserBinary: File? = null,
) : AbcBinary() {
    /** Output format for parsed AST. */
    public enum class DumpType(
        public val opt: String,
    ) {
        S_EXPR("--dump"),
        VAR("--var-dump"),
        JSON("--json-dump"),
    }

    private val preloadOsUniformer =
        mapOf(
            "mac" to "macos",
            "win" to "windows",
            "nix" to "linux",
            "nux" to "linux",
            "aix" to "linux",
        )

    private val preloadArchUniformer =
        mapOf(
            "aarch64" to "aarch64",
            "arm64" to "aarch64",
            "x86_64" to "x86_64",
            "amd64" to "x86_64",
        )

    private val preloadCrc32CheckSum =
        mapOf(
            "php-cli-$PHP_CLI_VERSION-linux-aarch64" to "714a9a7b",
            "php-cli-$PHP_CLI_VERSION-linux-x86_64" to "afd3bd14",
            "php-cli-$PHP_CLI_VERSION-macos-aarch64" to "7a3d2fca",
            "php-cli-$PHP_CLI_VERSION-macos-x86_64" to "3500f339",
            "php-cli-$PHP_CLI_VERSION-windows-x86_64" to "ef39e63d",
            "php-parser-$PARSER_VERSION" to "95f828b5",
        )

    private val phpBinaryFile: File = resolvePhpBinary(phpBinary)

    private val parserBinaryFile: File = parserBinary ?: resolveBundledParser()

    // Validates a caller-supplied interpreter or resolves one from the bundle or the system PATH.
    private fun resolvePhpBinary(phpBinary: File?): File {
        if (phpBinary != null) {
            if (!isPhpVersionValid(phpBinary, MIN_PHP_VERSION)) {
                throw ExternalBinaryInvalidException(phpBinary.absolutePath, "PHP version is below $MIN_PHP_VERSION")
            }
            return phpBinary
        }
        val rawOsName = System.getProperty("os.name", "unknown").lowercase()
        val uniOsName = preloadOsUniformer.firstNotNullOfOrNull { (k, v) -> v.takeIf { k in rawOsName } }
        val rawArchName = System.getProperty("os.arch", "unknown").lowercase()
        val uniArchName = preloadArchUniformer.firstNotNullOfOrNull { (k, v) -> v.takeIf { k in rawArchName } }
        if (uniOsName == null || uniArchName == null) {
            return searchSystemPhp() ?: throw ExternalBinaryNotFoundException(
                "php$MIN_PHP_VERSION+",
                "no bundled build for os=$rawOsName arch=$rawArchName; sys paths",
            )
        }
        return resolveBundledPhp("php-cli-$PHP_CLI_VERSION-$uniOsName-$uniArchName")
    }

    // Reuses a checksum-verified extraction or re-extracts the bundled interpreter for this platform.
    private fun resolveBundledPhp(fileName: String): File {
        val expFilePath = this.workTmpDir / fileName // the work tmp dir of the tool located in the tmp dir of sys
        if (expFilePath.crc32ChecksumString == preloadCrc32CheckSum[fileName]) return expFilePath.toFile()
        val loadStream = Thread.currentThread().contextClassLoader.getResourceAsStream("$fileName.zip")
        if (loadStream == null) {
            return searchSystemPhp()
                ?: throw ExternalBinaryNotFoundException("php$MIN_PHP_VERSION+", "resources or sys paths")
        }
        extractFileFromZip(loadStream, expFilePath, Path("php"), Path("php.exe"))
        return expFilePath.toFile().apply { setExecutable(true) }
    }

    // Reuses a checksum-verified extraction or re-extracts the bundled php-parser PHAR.
    private fun resolveBundledParser(): File {
        val fileName = "php-parser-$PARSER_VERSION"
        val expFilePath = this.workTmpDir / fileName
        if (expFilePath.crc32ChecksumString == preloadCrc32CheckSum[fileName]) return expFilePath.toFile()
        val loadStream =
            Thread.currentThread().contextClassLoader.getResourceAsStream("$fileName.zip")
                ?: throw ExternalBinaryNotFoundException("$fileName.zip", "the classpath resources")
        extractFileFromZip(loadStream, expFilePath, Path("php-parser.phar"))
        return expFilePath.toFile()
    }

    private fun searchSystemPhp(): File? = searchBin("php")?.takeIf { isPhpVersionValid(it, MIN_PHP_VERSION) }

    /**
     * The target PHP file to be parsed.
     */
    public var target: File by Argument("entryFile")

    /**
     * The type of dump output to produce, defaults to simple expression output.
     */
    public var dumpType: DumpType by Argument("dumpType", DumpType.S_EXPR)

    /** Pretty-print the AST output. */
    public var doPrettyPrint: Boolean by Option("--pretty-print", false)

    /** Resolve names in the AST using NodeVisitor\NameResolver. */
    public var doResolveName: Boolean by Option("--resolve-names", false)

    /** Include column information in output. */
    public var doWithColInfo: Boolean by Option("--with-column-info", false)

    /** Include position information in output. */
    public var doWithPositions: Boolean by Option("--with-positions", false)

    /** Recover from parse errors instead of failing. */
    public var doWithRecovery: Boolean by Option("--with-recovery", false)

    override fun getCommandArray(): Array<String> =
        buildList {
            add(phpBinaryFile.absolutePath)
            add(parserBinaryFile.absolutePath)
            for ((key, value) in allOptions) if (value is Boolean && value) add(key)
            add(dumpType.opt)
            add(target.absolutePath)
        }.toTypedArray()
}
