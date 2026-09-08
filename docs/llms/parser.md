# Parser API

> External PHP binary management and AST parsing via nikic/PHP-Parser v5.7.0.

## Quick Start

```kotlin
val parser = BinPhpParser().apply {
    dumpType = BinPhpParser.DumpType.JSON
    doResolveName = true
}
parser.target = File("path/to/file.php")
val result = parser.execute()
if (result.code == 0) println(result.output.readText())
```

## API

### BinPhpParser

**`BinPhpParser(phpBinary: File? = null, parserBinary: File? = null)`** — Construct parser. Resolves PHP binary (bundled ZIP > system PATH). Pass explicit binaries to override. Raises `ExternalBinaryNotFoundException` if no PHP found; raises `ExternalBinaryInvalidException` if `phpBinary` is below PHP 7.1.

**`target: File`** — PHP source file to parse. Required. Raises `ExternalBinaryArgumentMissException` if unset at `execute()`.

**`dumpType: DumpType`** — Output format. Values: `S_EXPR` (default), `VAR`, `JSON`. `DumpType.opt` holds the CLI flag.

**`doPrettyPrint: Boolean`** — Enable `--pretty-print`. Default `false`.

**`doResolveName: Boolean`** — Enable `--resolve-names`. Default `false`.

**`doWithColInfo: Boolean`** — Enable `--with-column-info`. Default `false`.

**`doWithPositions: Boolean`** — Enable `--with-positions`. Default `false`.

**`doWithRecovery: Boolean`** — Enable `--with-recovery`. Default `false`.

### AbcBinary (superclass)

**`execute(): BinaryResult`** — Run the external binary. Returns cached result if `doCacheOutput` is `true` and cache hit. Returns code `-1` after the fixed 1-minute timeout; the timeout is not configurable.

**`doCacheOutput: Boolean`** — Cache output by command content hash. Default `false`.

**`workTmpDir: Path`** — Working directory for output and cache files. Default `{java.io.tmpdir}/cobra/binaries/BinPhpParser`.

### BinaryResult

**`BinaryResult(code: Int, output: File)`** — Execution outcome.

- `code`: `0` = success, `-1` = timeout, other values = process exit code.
- `output`: `File` containing stdout+stderr.

### Top-Level Functions

**`executeWith(tmpConfig: T.() -> Unit): BinaryResult`** — Execute with temporary config. Backs up and restores all arguments/options.

**`searchBin(name: String): File?`** — Search system PATH for executable. Returns first match or `null`.

**`searchBin(under: Path, vararg possibleNames: String): File?`** — Look up an executable as a direct child of a directory.

**`isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean = true): Boolean`** — Check PHP version. Raises `ExternalBinaryInvalidException` on invalid format or unrunnable binary.

**`extractFileFromZip(zipInputStream: InputStream, toOutPath: Path, vararg fromZipPath: Path)`** — Extract one ZIP entry to `toOutPath` atomically. Raises `ExternalBinaryNotFoundException` when no entry matches.

**`Path.crc32ChecksumString: String?`**, **`File.crc32ChecksumString: String?`** — CRC32 of a regular file as 8 lowercase hex chars; `null` when absent.

### Exceptions

**`ExternalBinaryNotFoundException(name, under)`** — Binary not found. Raised during construction.

**`ExternalBinaryInvalidException(name, reason, cause)`** — Binary exists but fails validation.

**`ExternalBinaryArgumentMissException(argName)`** — Required argument (`target`) not set.

## Configuration

| Option | Property | CLI Flag | Effect |
|--------|----------|----------|--------|
| Dump S-expr | `DumpType.S_EXPR` | `--dump` | Human-readable indented dump (default) |
| Dump JSON | `DumpType.JSON` | `--json-dump` | JSON array of Stmt nodes with `nodeType` + `attributes` + subnodes |
| Dump var | `DumpType.VAR` | `--var-dump` | PHP `var_dump()` output for exact structure inspection |
| Pretty print | `doPrettyPrint` | `--pretty-print` | Regenerate PHP source from AST (round-trip test) |
| Name resolution | `doResolveName` | `--resolve-names` | Apply `NameResolver` visitor — resolves use/alias/namespace names to FQN |
| Column info | `doWithColInfo` | `--with-column-info` | Add column numbers to error messages |
| Positions | `doWithPositions` | `--with-positions` | Add `startFilePos`/`endFilePos` to node dumps |
| Recovery | `doWithRecovery` | `--with-recovery` | Parse broken PHP — inserts `Expr_Error` placeholder nodes |

- Bundled binaries are extracted under `workTmpDir`; set it before construction-time extraction is needed again only by creating a new parser.
- System property `java.io.tmpdir` sets the default `workTmpDir` root.
- Environment variable `PATH` is searched for `php` when no bundled build matches the host.

## Gotchas

- Construction resolves binaries eagerly. Fails fast if no PHP available.
- `executeWith { }` restores state on return and on exception.
- Output caching keys on the command, not file content. Changing file content without changing its path does not invalidate the cache.
- Only exit code `0` results are cached; failures re-execute.
- Bundled PHP: macOS x86_64/aarch64, Linux x86_64/aarch64, Windows x86_64.
- `--resolve-names` does not resolve `self`/`parent`/`static` or unqualified function/constant names. Full resolution behavior: [php-parser-guide.md](php-parser-guide.md).
