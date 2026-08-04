# externs-phpparser Design

## Design Overview

- **Classes**: `AbcBinary`, `BinPhpParser`, `BinaryResult`
- **Relationships**: `BinPhpParser` extends `AbcBinary`, `AbcBinary.execute()` returns `BinaryResult`
- **Abstract**: `AbcBinary` (implemented by `BinPhpParser`)
- **Exceptions**: `ExternalBinaryNotFoundException`, `ExternalBinaryInvalidException`, `ExternalBinaryArgumentMissException` — all extend `RuntimeException`
- **Dependency roles**: Data holders: `BinaryResult`, `BinPhpParser.DumpType`. Orchestrator: `BinPhpParser`. Helper: `AbcBinary` (process lifecycle framework, inputs by subclass override).

`AbcBinary` and `BinaryResult` live in the `binary` subpackage; everything else lives in the root package `edu.jhu.cobra.externs.phpparser`. `AbcBinary` defines the process execution framework: argument/option management via delegated properties, command array construction (abstract), process spawning bounded by a fixed liveness backstop, and output caching. `BinPhpParser` extends it with PHP-specific binary resolution (bundled extraction with CRC32 or system PATH search), platform normalization, and parser CLI flag assembly. `BinaryResult` is a passive data holder pairing exit code with output file reference. Stateless top-level helpers are split by responsibility: `ExecutableSearch.kt` (binary lookup), `PhpVersionValidation.kt` (interpreter version probing and comparison), `ArchiveExtraction.kt` (ZIP extraction and CRC32 checksums), and `ScopedExecution.kt` (execution under temporary configuration).

## Class / Type Specifications

### AbcBinary

**Responsibility**: Abstract framework for configuring, executing, and caching external binary processes.

**State/Fields**:
- `workTmpDir: Path` (public) — Working directory for process execution and cache files. Defaults to `{tmpdir}/cobra/binaries/{className}`.
- `allArguments: MutableMap<String, Any?>` (internal) — Registry of named arguments managed by `Argument` delegates. Mutated outside the class only through `withConfigurationSnapshot`.
- `allOptions: MutableMap<String, Any>` (internal) — Registry of named options managed by `Option` delegates. Mutated outside the class only through `withConfigurationSnapshot`.
- `executionTimeoutMillis: Long` (internal, open) — Liveness backstop before process destruction. Fixed at the `EXECUTION_TIMEOUT_MILLIS` constant (1 minute); open only so test doubles can shorten waits. Never per-run configuration (`resource-bounds.md`).
- `terminationGraceMillis: Long` (internal, open) — Grace period before forcible termination. Fixed at the `TERMINATION_GRACE_MILLIS` constant (5 s); open only for test doubles.
- `doCacheOutput: Boolean` (public) — Whether to reuse cached output for identical commands. Defaults to false.

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
- **Behavior**: Creates working directory if absent. Computes cache key via SHA-1 over the NUL-joined command array. If caching enabled and cache file exists, returns cached result. Otherwise spawns process, redirects stdout+stderr to temp file, waits up to the execution backstop. On timeout, destroys the process (graceful, then forcible after the termination grace) and reaps it before returning.
- **Input**: None (reads from internal state configured via delegates).
- **Output**: `BinaryResult` — code 0 on success, -1 on timeout.
- **Errors**: OS-level exceptions if binary not found or not executable.

`withConfigurationSnapshot(block: () -> R): R` (internal)
- **Behavior**: Snapshots `allArguments` and `allOptions`, runs `block`, and restores both registries in a finally block — on normal completion and on exception.
- **Input**: `block` — computation run under mutable configuration.
- **Output**: The value returned by `block`.
- **Errors**: Propagates any exception from `block`; state is restored regardless.

**Example usage**:
```kotlin
val parser = BinPhpParser()
parser.target = File("example.php")
val result = parser.execute()
if (result.code == 0) println(result.output.readText())
```

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

### BinaryResult

**Responsibility**: Immutable data holder for an execution outcome.

**State/Fields**:
- `code: Int` — Process exit code. 0 = success, -1 = timeout.
- `output: File` — File containing captured stdout+stderr.

---

## Function Specifications

### ScopedExecution.kt

**`executeWith(tmpConfig: T.() -> Unit): BinaryResult`**
- **Responsibility**: Execute a binary with temporary configuration, restoring original state afterward.
- **Behavior**: Delegates to `AbcBinary.withConfigurationSnapshot`: applies `tmpConfig` and calls `execute()` under the snapshot; the snapshot restores both registries afterward.
- **Input**: `tmpConfig` — configuration lambda applied to the receiver `AbcBinary` subtype.
- **Output**: `BinaryResult` from the temporary execution.
- **Errors**: Propagates any exception from `execute()`. State is always restored — both normal returns and exceptions.

### ExecutableSearch.kt

**`searchBin(under: Path, vararg possibleNames: String): File?`**
- **Responsibility**: Look up an executable as a direct child of a directory.
- **Behavior**: Checks each candidate name as a direct child of `under`, returns the first that is an executable regular file.
- **Input**: `under` — directory; `possibleNames` — candidate file names.
- **Output**: First matching `File`, or null.
- **Errors**: None thrown; returns null on no match.

**`searchBin(name: String): File?`**
- **Responsibility**: Search system PATH for an executable by name.
- **Behavior**: On Windows, appends `.exe`/`.bat` suffixes. Lazily iterates PATH entries via `splitToSequence`, filters existing directories, delegates to `searchBin(Path, ...)`. Short-circuits on first match.
- **Input**: `name` — base executable name without extension.
- **Output**: First matching `File`, or null.
- **Errors**: None thrown; returns null if PATH unavailable or no match.

### PhpVersionValidation.kt

**`readPhpVersion(binary: File, probeTimeoutSeconds: Long): String`** (internal)
- **Responsibility**: Read the version reported by `php -v`.
- **Behavior**: Spawns `binary -v`, waits up to the probe backstop (`VERSION_PROBE_TIMEOUT_SECONDS`, 10 s), extracts the version via compiled regex. A hung probe is force-killed and reaped. Internal so tests can shorten the backstop; production callers use the default.
- **Input**: `binary` — PHP executable; `probeTimeoutSeconds` — liveness backstop, defaults to the constant.
- **Output**: Version string `major.minor.patch`.
- **Errors**: Throws `ExternalBinaryInvalidException` when the binary cannot run, the probe times out, or the output carries no version.

**`isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean): Boolean`**
- **Responsibility**: Check whether a PHP binary meets a minimum version requirement.
- **Behavior**: Validates `minRequired` format, reads the current version via `readPhpVersion`, compares major.minor.patch components numerically.
- **Input**: `binary` — PHP executable; `minRequired` — version string (1-3 components); `includeEqual` — whether equality satisfies the check (default true).
- **Output**: `true` if current version meets requirement.
- **Errors**: Throws `ExternalBinaryInvalidException` if `minRequired` has invalid format, or propagated from `readPhpVersion`.

### ArchiveExtraction.kt

**`extractFileFromZip(zipInputStream: InputStream, toOutPath: Path, vararg fromZipPath: Path)`**
- **Responsibility**: Extract a single target file from a ZIP archive with atomic publication.
- **Behavior**: Creates parent directories, iterates ZIP entries with path normalization (backslash to forward slash), stages the first matching entry to a unique sibling temp file, then moves it over the destination via `ATOMIC_MOVE` (falling back to `REPLACE_EXISTING` when the filesystem lacks atomic-move support). Concurrent readers only ever observe an absent or complete destination, never a partial one. The staging file is removed on every exit path.
- **Input**: `zipInputStream` — ZIP stream; `toOutPath` — extraction destination; `fromZipPath` — candidate entry paths within ZIP.
- **Output**: None (Unit); the destination file exists on return.
- **Errors**: Throws `ExternalBinaryNotFoundException` when no entry matches. Propagates I/O exceptions from stream operations.

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
| `ExternalBinaryInvalidException` | `RuntimeException` | A binary exists but fails validation — invalid version format, unrunnable or hung version probe, unparsable version output, or an extracted interpreter that cannot be marked executable. |
| `ExternalBinaryArgumentMissException` | `RuntimeException` | A required `Argument` delegate is read before being set. Raised when accessing `target` without assignment. |

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
- Liveness backstop enforced: process destroyed after the fixed `EXECUTION_TIMEOUT_MILLIS` backstop, returns code -1. The backstop is a constant, never per-run configuration.

### BinPhpParser (bundled extraction)
- An extracted interpreter that cannot be marked executable throws `ExternalBinaryInvalidException`.
- A pre-existing extraction whose CRC32 matches the preloaded checksum is reused; extraction is skipped.
