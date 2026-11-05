package edu.jhu.cobra.externs.phpparser

import edu.jhu.cobra.externs.phpparser.binary.AbcBinary
import edu.jhu.cobra.externs.phpparser.binary.BinaryResult
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.div

// Lowest interpreter version able to run the bundled php-parser PHAR: its Composer platform check requires 7.4.
private const val MIN_PHP_VERSION = "7.4"

// Interpreter release bundled as classpath zip resources.
private const val PHP_CLI_VERSION = "8.4"

/** The immutable bundled php-parser release, shared with consumers that key parsed graph caches. */
public const val PARSER_VERSION: String = "5.7.0"

// Substrings of `os.name` mapped to the platform token used in bundled resource names.
private val OS_TOKEN_BY_NAME_FRAGMENT =
    mapOf(
        "mac" to "macos",
        "win" to "windows",
        "nix" to "linux",
        "nux" to "linux",
        "aix" to "linux",
    )

// Substrings of `os.arch` mapped to the architecture token used in bundled resource names.
private val ARCH_TOKEN_BY_NAME_FRAGMENT =
    mapOf(
        "aarch64" to "aarch64",
        "arm64" to "aarch64",
        "x86_64" to "x86_64",
        "amd64" to "x86_64",
    )

// CRC32 of each bundled binary once extracted; fixed by the resource zips shipped on the classpath.
private val BUNDLED_BINARY_CRC32 =
    mapOf(
        "php-cli-$PHP_CLI_VERSION-linux-aarch64" to "714a9a7b",
        "php-cli-$PHP_CLI_VERSION-linux-x86_64" to "afd3bd14",
        "php-cli-$PHP_CLI_VERSION-macos-aarch64" to "7a3d2fca",
        "php-cli-$PHP_CLI_VERSION-macos-x86_64" to "3500f339",
        "php-cli-$PHP_CLI_VERSION-windows-x86_64" to "ef39e63d",
        "php-parser-$PARSER_VERSION" to "95f828b5",
    )

// Classpath resource of the Dump Script, run with the parser PHAR as its first argument.
private const val DUMP_SCRIPT_RESOURCE = "php_parse.php"

// Immutable per-run settings; copying it is the only way a setter changes the parser.
private data class ParseConfig(
    val target: File? = null,
    val dumpType: BinPhpParser.DumpType = BinPhpParser.DumpType.S_EXPR,
    val doPrettyPrint: Boolean = false,
    val doResolveName: Boolean = false,
    val doWithColInfo: Boolean = false,
    val doWithPositions: Boolean = false,
    val doWithRecovery: Boolean = false,
)

/**
 * Parses PHP files into ASTs using the php-parser binary.
 *
 * @param phpBinary PHP interpreter binary, or null to auto-detect.
 * @param parserBinary php-parser PHAR, which the Dump Script loads, or null to auto-detect.
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

    private val phpBinaryFile: File = resolvePhpBinary(phpBinary)

    private val parserBinaryFile: File = parserBinary ?: resolveBundledParser()

    private val dumpScriptFile: File = resolveDumpScript()

    // Validates a caller-supplied interpreter or resolves one from the bundle or the system PATH.
    private fun resolvePhpBinary(phpBinary: File?): File {
        if (phpBinary != null) {
            if (!isPhpVersionValid(phpBinary, MIN_PHP_VERSION)) {
                throw ExternalBinaryInvalidException(phpBinary.absolutePath, "PHP version is below $MIN_PHP_VERSION")
            }
            return phpBinary
        }
        val rawOsName = System.getProperty("os.name", "unknown").lowercase()
        val uniOsName = OS_TOKEN_BY_NAME_FRAGMENT.firstNotNullOfOrNull { (k, v) -> v.takeIf { k in rawOsName } }
        val rawArchName = System.getProperty("os.arch", "unknown").lowercase()
        val uniArchName = ARCH_TOKEN_BY_NAME_FRAGMENT.firstNotNullOfOrNull { (k, v) -> v.takeIf { k in rawArchName } }
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
        val expFilePath = this.workTmpDir / fileName
        if (expFilePath.crc32ChecksumString == BUNDLED_BINARY_CRC32[fileName]) return expFilePath.toFile()
        val loadStream = Thread.currentThread().contextClassLoader.getResourceAsStream("$fileName.zip")
        if (loadStream == null) {
            return searchSystemPhp()
                ?: throw ExternalBinaryNotFoundException("php$MIN_PHP_VERSION+", "resources or sys paths")
        }
        extractFileFromZip(loadStream, expFilePath, Path("php"), Path("php.exe"))
        val extractedBinary = expFilePath.toFile()
        if (!extractedBinary.setExecutable(true)) {
            throw ExternalBinaryInvalidException(
                extractedBinary.absolutePath,
                "could not mark the extracted interpreter executable",
            )
        }
        return extractedBinary
    }

    // Reuses a checksum-verified extraction or re-extracts the bundled php-parser PHAR.
    private fun resolveBundledParser(): File {
        val fileName = "php-parser-$PARSER_VERSION"
        val expFilePath = this.workTmpDir / fileName
        if (expFilePath.crc32ChecksumString == BUNDLED_BINARY_CRC32[fileName]) return expFilePath.toFile()
        val loadStream =
            Thread.currentThread().contextClassLoader.getResourceAsStream("$fileName.zip")
                ?: throw ExternalBinaryNotFoundException("$fileName.zip", "the classpath resources")
        extractFileFromZip(loadStream, expFilePath, Path("php-parser.phar"))
        return expFilePath.toFile()
    }

    // Publishes the Dump Script under its checksum, so a changed script is a changed command line.
    private fun resolveDumpScript(): File {
        val scriptBytes =
            Thread
                .currentThread()
                .contextClassLoader
                .getResourceAsStream(DUMP_SCRIPT_RESOURCE)
                ?.use { it.readBytes() }
                ?: throw ExternalBinaryNotFoundException(DUMP_SCRIPT_RESOURCE, "the classpath resources")
        val scriptCrc32 = scriptBytes.crc32ChecksumString
        val expFilePath = this.workTmpDir / "php_parse-$scriptCrc32.php"
        if (expFilePath.crc32ChecksumString != scriptCrc32) publishFile(scriptBytes.inputStream(), expFilePath)
        return expFilePath.toFile()
    }

    private fun searchSystemPhp(): File? = searchBin("php")?.takeIf { isPhpVersionValid(it, MIN_PHP_VERSION) }

    // Per-run configuration: the one value a scoped execution saves and restores.
    private var config = ParseConfig()

    /**
     * The target PHP file to be parsed.
     *
     * @throws ExternalBinaryArgumentMissException when read before it is set.
     */
    public var target: File
        get() = config.target ?: throw ExternalBinaryArgumentMissException(TARGET_ARGUMENT)
        set(value) {
            config = config.copy(target = value)
        }

    /**
     * The type of dump output to produce, defaults to simple expression output.
     */
    public var dumpType: DumpType
        get() = config.dumpType
        set(value) {
            config = config.copy(dumpType = value)
        }

    /** Pretty-print the AST output. */
    public var doPrettyPrint: Boolean
        get() = config.doPrettyPrint
        set(value) {
            config = config.copy(doPrettyPrint = value)
        }

    /** Resolve names in the AST using NodeVisitor\NameResolver. */
    public var doResolveName: Boolean
        get() = config.doResolveName
        set(value) {
            config = config.copy(doResolveName = value)
        }

    /** Include column information in output. */
    public var doWithColInfo: Boolean
        get() = config.doWithColInfo
        set(value) {
            config = config.copy(doWithColInfo = value)
        }

    /** Include position information in output. */
    public var doWithPositions: Boolean
        get() = config.doWithPositions
        set(value) {
            config = config.copy(doWithPositions = value)
        }

    /** Recover from parse errors instead of failing. */
    public var doWithRecovery: Boolean
        get() = config.doWithRecovery
        set(value) {
            config = config.copy(doWithRecovery = value)
        }

    override fun getCommandArray(): Array<String> {
        val current = config
        val target = current.target ?: throw ExternalBinaryArgumentMissException(TARGET_ARGUMENT)
        val flags =
            listOf(
                current.doPrettyPrint to "--pretty-print",
                current.doResolveName to "--resolve-names",
                current.doWithColInfo to "--with-column-info",
                current.doWithPositions to "--with-positions",
                current.doWithRecovery to "--with-recovery",
            )
        return buildList {
            add(phpBinaryFile.absolutePath)
            add(dumpScriptFile.absolutePath)
            add(parserBinaryFile.absolutePath)
            for ((enabled, flag) in flags) if (enabled) add(flag)
            add(current.dumpType.opt)
            add(target.absolutePath)
        }.toTypedArray()
    }

    /**
     * Applies [tmpConfig] to this parser, executes, and restores the target, dump type and flags
     * afterward, on normal completion, exception and interrupt. [workTmpDir] and [doCacheOutput]
     * assigned in [tmpConfig] persist.
     *
     * @param tmpConfig configuration applied before execution.
     * @return the execution result.
     */
    public fun executeWith(tmpConfig: BinPhpParser.() -> Unit): BinaryResult {
        val saved = config
        try {
            tmpConfig()
            return execute()
        } finally {
            config = saved
        }
    }
}

// Name the missing-argument exception reports for the target file.
private const val TARGET_ARGUMENT = "entryFile"
