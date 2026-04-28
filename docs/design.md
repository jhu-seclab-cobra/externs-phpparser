# externs-phpparser Design

## Design Overview

- **Classes**: `AbcBinary`, `BinPhpParser`, `BinaryResult`
- **Relationships**: `BinPhpParser` extends `AbcBinary`, `AbcBinary.execute()` returns `BinaryResult`
- **Abstract**: `AbcBinary` (implemented by `BinPhpParser`)
- **Exceptions**: `ExternalBinaryNotFoundException` extends `RuntimeException`, `ExternalBinaryInvalidException` extends `RuntimeException`, `ExternalBinaryArgumentMissException` extends `Exception`
- **Dependency roles**: Data holders: `BinaryResult`, `BinPhpParser.DumpType`. Orchestrator: `BinPhpParser`. Helper: `AbcBinary` (process lifecycle framework, inputs by subclass override).

`AbcBinary` defines the process execution framework: argument/option management via delegated properties, command array construction (abstract), process spawning with timeout, and output caching. `BinPhpParser` extends it with PHP-specific binary resolution (bundled extraction with CRC32 or system PATH search), platform normalization, and parser CLI flag assembly. `BinaryResult` is a passive data holder pairing exit code with output file reference. Utility functions in `Utils.kt` provide binary search, version validation, ZIP extraction, and CRC32 checksum computation as stateless helpers.

## Class / Type Specifications

### AbcBinary

**Responsibility**: Abstract framework for configuring, executing, and caching external binary processes.

**State/Fields**:
- `workTmpDir: Path` — Working directory for process execution and cache files. Defaults to `{tmpdir}/cobra/binaries/{className}`.
- `allArguments: MutableMap<String, Any?>` — Registry of named arguments managed by `Argument` delegates.
- `allOptions: MutableMap<String, Any>` — Registry of named options managed by `Option` delegates.
- `timeout: Duration` — Maximum execution time before process destruction. Defaults to 1 minute.
- `doCacheOutput: Boolean` — Whether to reuse cached output for identical commands. Defaults to false.

**Inner Classes**:
- `Argument<T>` — `ReadWriteProperty` delegate that reads/writes `allArguments[name]`. Throws `ExternalBinaryArgumentMissException` on read if value is null.
- `Option<T>` — `ReadWriteProperty` delegate that reads/writes `allOptions[name]`. Returns null-cast on read if absent (no exception).

**Methods**:

`getCommandArray(): Array<String>` (abstract)
- **Behavior**: Constructs the full command-line array for the external process.
- **Input**: None (reads from internal state).
- **Output**: `Array<String>` — command and arguments.
- **Errors**: Implementation-specific.

`execute(): BinaryResult` (open)
- **Behavior**: Creates working directory if absent. Computes cache key via `contentHashCode()` of command array. If caching enabled and cache file exists, returns cached result. Otherwise spawns process, redirects stdout+stderr to temp file, waits up to `timeout`. On timeout, destroys process.
- **Input**: None (reads from internal state configured via delegates).
- **Output**: `BinaryResult` — code 0 on success, -1 on timeout.
- **Errors**: OS-level exceptions if binary not found or not executable.

**Example usage**:
```kotlin
val parser = BinPhpParser()
parser.target = File("example.php")
val result = parser.execute()
if (result.code == 0) println(result.output.readText())
```

---

### BinPhpParser

**Responsibility**: PHP-specific binary resolver and AST parser that configures and executes the php-parser binary.

**State/Fields**:
- `phpBinaryFile: File` — Resolved PHP interpreter (bundled or system). Resolved eagerly on construction.
- `parserBinaryFile: File` — Resolved php-parser PHAR. Resolved eagerly on construction.
- `target: File` — (Argument) Target PHP source file to parse. Required before `execute()`.
- `dumpType: DumpType` — (Argument) AST output format. Defaults to `S_EXPR`.
- `doPrettyPrint: Boolean` — (Option `--pretty-print`) Defaults to false.
- `doResolveName: Boolean` — (Option `--resolve-names`) Applies `NodeVisitor\NameResolver`. Defaults to false.
- `doWithColInfo: Boolean` — (Option `--with-column-info`) Defaults to false.
- `doWithPositions: Boolean` — (Option `--with-positions`) Defaults to false.
- `doWithRecovery: Boolean` — (Option `--with-recovery`) Defaults to false.
- `preloadOsUniformer: Map` — OS name normalization map.
- `preloadArchUniformer: Map` — Architecture name normalization map.
- `preloadCrc32CheckSum: Map` — Known-good CRC32 checksums for bundled binaries.

**Inner Types**:

`DumpType` (enum)
- `S_EXPR("--dump")`, `VAR("--var-dump")`, `JSON("--json-dump")`
- `toString()` returns the CLI flag string.

**Methods**:

`getCommandArray(): Array<String>` (override)
- **Behavior**: Builds command array via single-pass `buildList`: PHP binary, parser PHAR, enabled boolean options, dump type flag, target path.
- **Input**: Reads `phpBinaryFile`, `parserBinaryFile`, `allOptions`, `dumpType`, `target`.
- **Output**: `Array<String>`.
- **Errors**: `ExternalBinaryArgumentMissException` if `target` not set.

**Construction (init)**:
- PHP binary resolution: user-provided (with version validation >= 7.1) > bundled ZIP extraction (with CRC32 check) > system PATH search. Throws `ExternalBinaryNotFoundException` if all fail.
- Parser binary resolution: user-provided > bundled ZIP extraction (with CRC32 check). Throws `ExternalBinaryNotFoundException` if extraction fails.

---

### BinaryResult

**Responsibility**: Immutable data holder for an execution outcome.

**State/Fields**:
- `code: Int` — Process exit code. 0 = success, -1 = timeout.
- `output: File` — File containing captured stdout+stderr.

---

## Function Specifications

### Utils.kt — Global Functions

**`executeWith(tmpConfig: T.() -> Unit): BinaryResult`**
- **Responsibility**: Execute a binary with temporary configuration, restoring original state afterward.
- **Behavior**: Backs up `allArguments` and `allOptions` into snapshot copies, applies `tmpConfig` lambda, calls `execute()` inside try-finally, restores backup (clear + putAll) in the finally block.
- **Input**: `tmpConfig` — configuration lambda applied to the receiver `AbcBinary` subtype.
- **Output**: `BinaryResult` from the temporary execution.
- **Errors**: Propagates any exception from `execute()`. State is always restored via try-finally — both normal returns and exceptions trigger the finally block.

**`searchBin(under: Path, vararg possibleNames: String): File?`**
- **Responsibility**: Search a directory tree for a file matching any of the given names.
- **Behavior**: Walks directory top-down, returns first file whose name matches.
- **Input**: `under` — root directory; `possibleNames` — candidate file names.
- **Output**: First matching `File`, or null.
- **Errors**: None thrown; returns null on no match.

**`searchBin(name: String): File?`**
- **Responsibility**: Search system PATH for an executable by name.
- **Behavior**: On Windows, appends `.exe`/`.bat` suffixes. Lazily iterates PATH entries via `splitToSequence`, filters existing directories, delegates to `searchBin(Path, ...)`. Short-circuits on first match.
- **Input**: `name` — base executable name without extension.
- **Output**: First matching `File`, or null.
- **Errors**: None thrown; returns null if PATH unavailable or no match.

**`isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean): Boolean`**
- **Responsibility**: Check whether a PHP binary meets a minimum version requirement.
- **Behavior**: Spawns `php -v`, extracts version via compiled regex, compares major.minor.patch components numerically.
- **Input**: `binary` — PHP executable; `minRequired` — version string (1-3 components); `includeEqual` — whether equality satisfies the check (default true).
- **Output**: `true` if current version meets requirement.
- **Errors**: Throws `ExternalBinaryInvalidException` if either version string has invalid format.

**`extractFileFromZip(zipInputStream: InputStream, toOutPath: Path, vararg fromZipPath: Path): Boolean`**
- **Responsibility**: Extract a single target file from a ZIP archive.
- **Behavior**: Creates parent directories, iterates ZIP entries with path normalization (backslash to forward slash), copies first matching entry to destination.
- **Input**: `zipInputStream` — ZIP stream; `toOutPath` — extraction destination; `fromZipPath` — candidate entry paths within ZIP.
- **Output**: `true` if a matching entry was found and extracted.
- **Errors**: Propagates I/O exceptions from stream operations.

**`Path.crc32ChecksumString: String?`** (extension property)
- **Responsibility**: Compute CRC32 checksum of a file for integrity verification.
- **Behavior**: Validates file existence, reads in 16KB chunks, returns 8-character lowercase hex string.
- **Input**: Receiver `Path`.
- **Output**: Hex checksum string, or null if file doesn't exist or isn't regular.
- **Errors**: Propagates I/O exceptions from file reading.

**`File.crc32ChecksumString: String?`** (extension property)
- **Responsibility**: Convenience delegate to `Path.crc32ChecksumString`.

---

## Exception / Error Types

| Exception | Superclass | Raised When |
|-----------|-----------|-------------|
| `ExternalBinaryNotFoundException` | `RuntimeException` | Binary resolution fails — not found in provided path, bundled resources, or system PATH. Raised during `BinPhpParser` construction. |
| `ExternalBinaryInvalidException` | `RuntimeException` | A binary exists but fails validation — invalid version format in `isPhpVersionValid`. |
| `ExternalBinaryArgumentMissException` | `Exception` | A required `Argument` delegate is read before being set. Raised when accessing `target` without assignment. |

---

## Validation Rules

### BinPhpParser (construction)
- PHP binary: must be a valid PHP >= 7.1 executable (validated via `isPhpVersionValid`), or a bundled binary whose CRC32 matches the preloaded checksum.
- Parser binary: must be extractable from bundled ZIP with matching CRC32, or provided by user.
- If all resolution strategies fail, construction throws `ExternalBinaryNotFoundException`.

### isPhpVersionValid
- Both `current` (extracted) and `minRequired` version strings must match `^\d+(\.\d+){0,2}$`. Invalid format throws `ExternalBinaryInvalidException`.

### AbcBinary.Argument (read)
- Value must be non-null. Null value throws `ExternalBinaryArgumentMissException`.

### AbcBinary.execute
- `workTmpDir` is created if absent (no validation — delegates to filesystem).
- Timeout enforced: process destroyed after `timeout` duration, returns code -1.
