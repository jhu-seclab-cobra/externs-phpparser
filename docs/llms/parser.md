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

**`BinPhpParser(phpBinary: File? = null, parserBinary: File? = null)`** — Construct parser. Resolves PHP binary (bundled ZIP > system PATH). Pass explicit binaries to override. Raises `ExternalBinaryNotFoundException` if no PHP found.

**`target: File`** — PHP source file to parse. Required. Raises `ExternalBinaryArgumentMissException` if unset at `execute()`.

**`dumpType: DumpType`** — Output format. Values: `S_EXPR` (default), `VAR`, `JSON`.

**`doPrettyPrint: Boolean`** — Enable `--pretty-print`. Default `false`.

**`doResolveName: Boolean`** — Enable `--resolve-names`. Default `false`.

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

**`isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean = true): Boolean`** — Check PHP version. Raises `ExternalBinaryInvalidException` on invalid format.

### Exceptions

**`ExternalBinaryNotFoundException`** — Binary not found. Raised during construction.

**`ExternalBinaryInvalidException`** — Binary exists but fails validation.

**`ExternalBinaryArgumentMissException`** — Required argument (`target`) not set.

## CLI Options Reference

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

## Name Resolution (`--resolve-names`)

Applies `NodeVisitor\NameResolver`. Resolves most names to `Name_FullyQualified`:

| Resolved (Name_FullyQualified) | Not resolved (Name) |
|-------------------------------|---------------------|
| `use` imports and aliases | `self` — needs class context |
| Qualified names (`Models\User`) | `parent` — needs extends context |
| `namespace\` relative names | `static` — runtime late binding |
| `extends`/`implements` class refs | Unqualified functions (`strlen`) |
| `new ClassName()` | Unqualified constants (`PHP_INT_MAX`) |

Unqualified functions/constants get a `namespacedName` attribute with the namespace-prefixed version. `Stmt_Namespace` and `Stmt_Use` nodes remain in AST.

## Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `target` | `File` | (required) | PHP source file to parse |
| `dumpType` | `DumpType` | `S_EXPR` | Output format |
| `doPrettyPrint` | `Boolean` | `false` | Pretty-print AST |
| `doResolveName` | `Boolean` | `false` | Resolve names to FQN |
| `doWithColInfo` | `Boolean` | `false` | Column info in errors |
| `doWithPositions` | `Boolean` | `false` | File positions in dump |
| `doWithRecovery` | `Boolean` | `false` | Error recovery mode |
| `timeout` | `Duration` | 1 minute | Max execution time |
| `doCacheOutput` | `Boolean` | `false` | Cache by command hash |

## Gotchas

- Construction resolves binaries eagerly. Fails fast if no PHP available.
- `executeWith { }` restores state via try-finally.
- Cache key is `contentHashCode()` of command array. Changing file content without changing path does not invalidate.
- Bundled PHP: macOS x86_64/aarch64, Linux x86_64/aarch64, Windows x86_64.
- `DumpType.JSON` output is prefixed with `====> File ...` header lines. Skip non-JSON prefix before parsing.
- `--resolve-names` does not resolve `self`/`parent`/`static` or unqualified function/constant names.
