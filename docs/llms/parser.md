# Parser API

> External PHP binary management and AST parsing.

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

**`BinPhpParser()`** — Construct parser. Resolves PHP binary (bundled ZIP > system PATH). Raises `ExternalBinaryNotFoundException` if no PHP found.

**`target: File`** — PHP source file to parse. Required. Raises `ExternalBinaryArgumentMissException` if unset at `execute()`.

**`dumpType: DumpType`** — Output format. Values: `S_EXPR` (default), `VAR`, `JSON`.

**`doPrettyPrint: Boolean`** — Enable `--pretty-print`. Default `false`.

**`doResolveName: Boolean`** — Enable `--resolve-others`. Default `false`.

**`doWithColInfo: Boolean`** — Enable `--with-column-info`. Default `false`.

**`doWithPositions: Boolean`** — Enable `--with-positions`. Default `false`.

**`doWithRecovery: Boolean`** — Enable `--with-recovery`. Default `false`.

### AbcBinary (superclass)

**`execute(): BinaryResult`** — Run the external binary. Returns cached result if `doCacheOutput` is `true` and cache hit.

**`timeout: Duration`** — Max execution time. Default 1 minute. Process destroyed on timeout.

**`doCacheOutput: Boolean`** — Cache output by command content hash. Default `false`.

### BinaryResult

**`BinaryResult(code: Int, output: File)`** — Execution outcome.

- `code`: `0` = success, `-1` = timeout.
- `output`: `File` containing stdout+stderr.

### Utility Functions

**`executeWith(tmpConfig: T.() -> Unit): BinaryResult`** — Execute with temporary config. Backs up and restores all arguments/options.

**`searchBin(name: String): File?`** — Search system PATH for executable. Returns first match or `null`.

**`searchBin(under: Path, vararg possibleNames: String): File?`** — Search directory tree for file by name.

**`isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean = true): Boolean`** — Check PHP version. Raises `ExternalBinaryInvalidException` on invalid version format.

### Exceptions

**`ExternalBinaryNotFoundException`** — Binary not found in provided path, bundled resources, or system PATH. Raised during `BinPhpParser` construction.

**`ExternalBinaryInvalidException`** — Binary exists but fails validation (invalid version format).

**`ExternalBinaryArgumentMissException`** — Required argument (`target`) read before being set.

## Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `target` | `File` | (none, required) | PHP source file to parse |
| `dumpType` | `DumpType` | `S_EXPR` | Output format: `S_EXPR`, `VAR`, `JSON` |
| `doPrettyPrint` | `Boolean` | `false` | Pretty-print AST output |
| `doResolveName` | `Boolean` | `false` | Resolve names in AST |
| `doWithColInfo` | `Boolean` | `false` | Include column info |
| `doWithPositions` | `Boolean` | `false` | Include position info |
| `doWithRecovery` | `Boolean` | `false` | Error recovery mode |
| `timeout` | `Duration` | 1 minute | Max execution time |
| `doCacheOutput` | `Boolean` | `false` | Cache output for identical commands |

## Gotchas

- `BinPhpParser` resolves binaries eagerly at construction. Construction fails fast if no PHP binary is available.
- `executeWith { }` restores state even if execution throws.
- Cache key is `contentHashCode()` of the command array. Changing the source file content without changing the file path does not invalidate the cache.
- Bundled PHP platforms: macOS x86_64/aarch64, Linux x86_64/aarch64, Windows x86_64. Other platforms need PHP 7.1+ on system PATH.
- `DumpType.JSON` produces a JSON array of statement nodes. Each node has `nodeType`, `attributes`, and type-specific subnodes.
