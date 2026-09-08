# externs-phpparser Design

## Design Overview

- **Classes**: `AbcBinary`, `BinPhpParser`, `BinaryResult`
- **Relationships**: `BinPhpParser` extends `AbcBinary`, `AbcBinary.execute()` returns `BinaryResult`
- **Abstract**: `AbcBinary` (implemented by `BinPhpParser`)
- **Exceptions**: `ExternalBinaryNotFoundException`, `ExternalBinaryInvalidException`, `ExternalBinaryArgumentMissException` — all extend `RuntimeException`
- **Dependency roles**: Data holders: `BinaryResult`, `BinPhpParser.DumpType`. Orchestrator: `BinPhpParser`. Helper: `AbcBinary` (process lifecycle framework, inputs by subclass override).
- **Modules and visibility**: One Gradle module (`externs-phpparser`, `explicitApi()`). `AbcBinary` and `BinaryResult` are public in the `binary` subpackage; `BinPhpParser`, the exceptions, and the top-level helpers are public in the root package `edu.jhu.cobra.externs.phpparser`. `readPhpVersion` and `withConfigurationSnapshot` are internal.
- **Named values** (all constant tier, `rules/code/constants.md`): `EXECUTION_TIMEOUT_MILLIS` and `TERMINATION_GRACE_MILLIS` (internal, `AbcBinary.kt`), `VERSION_PROBE_TIMEOUT_SECONDS` (internal) and `VERSION_COMPONENT_COUNT` (private, `PhpVersionValidation.kt`), `CRC_BUFFER_SIZE` (private, `ArchiveExtraction.kt`), `MIN_PHP_VERSION`, `PHP_CLI_VERSION`, `PARSER_VERSION` (private, `BinPhpParser.kt`).

`AbcBinary` defines the process execution framework: argument/option management via delegated properties, command array construction (abstract), process spawning bounded by a fixed liveness backstop, and output caching. `BinPhpParser` extends it with PHP-specific binary resolution (bundled extraction with CRC32 or system PATH search), platform normalization, and parser CLI flag assembly. `BinaryResult` is a passive data holder pairing exit code with output file reference. Stateless top-level helpers are split by responsibility: `ExecutableSearch.kt` (binary lookup), `PhpVersionValidation.kt` (interpreter version probing and comparison), `ArchiveExtraction.kt` (ZIP extraction and CRC32 checksums), and `ScopedExecution.kt` (execution under temporary configuration).

## Class / Type Specifications

### AbcBinary

**Responsibility**: Abstract framework for configuring, executing, and caching external binary processes. Public abstract class, `binary` subpackage.

**State/Fields**:
- `workTmpDir: Path` (public, mutable) — Working directory for process execution, output files, and cache files. Defaults to `{java.io.tmpdir}/cobra/binaries/{className}`.
- `allArguments: MutableMap<String, Any?>` (internal) — Registry of named arguments managed by `Argument` delegates. Mutated outside the class only through `withConfigurationSnapshot`.
- `allOptions: MutableMap<String, Any>` (internal) — Registry of named options managed by `Option` delegates. Mutated outside the class only through `withConfigurationSnapshot`.
- `executionTimeoutMillis: Long` (internal, open) — Liveness backstop before process destruction. Fixed at `EXECUTION_TIMEOUT_MILLIS` (1 minute); open only so test doubles can shorten waits. Never per-run configuration (`resource-bounds.md`).
- `terminationGraceMillis: Long` (internal, open) — Grace period before forcible termination. Fixed at `TERMINATION_GRACE_MILLIS` (5 s); open only for test doubles.
- `doCacheOutput: Boolean` (public, mutable) — Whether to reuse cached output for identical commands. Defaults to false.

**Inner Classes** (protected):
- `Argument<T>` — `ReadWriteProperty` delegate over `allArguments[name]`. Registers its default on declaration. Read throws `ExternalBinaryArgumentMissException` when the value is null. Assigning null removes the entry.
- `Option<T>` — `ReadWriteProperty` delegate over `allOptions[name]`. Registers a non-null default on declaration. Read of an absent entry returns null when declared with a null default, and raises `IllegalStateException` when declared with a non-null default (the value was removed). Assigning null removes the entry, excluding it from the command line.

**Methods**:

`getCommandArray(): Array<String>` (abstract)
- **Behavior**: Constructs the full command-line array for the external process.
- **Input**: None (reads from internal state).
- **Output**: `Array<String>` — command and arguments.
- **Errors**: Implementation-specific.

`execute(): BinaryResult` (open)
- **Behavior**: Creates the working directory if absent. Computes a cache key via SHA-1 over the NUL-joined command array. When caching is enabled and the cache file exists, returns it without spawning. Otherwise spawns the process with stdout+stderr redirected to a per-execution temp file (unique per call, so concurrent runs of one command never share an output file) and waits up to the execution backstop. On backstop expiry, destroys the process (graceful, then forcible after the termination grace), reaps it, and appends a timeout note to the output file. When caching is enabled and the exit code is 0, the output file is moved to the cache file. An interrupted wait reaps the process, re-sets the interrupt flag, and propagates `InterruptedException`.
- **Input**: None (reads from internal state configured via delegates).
- **Output**: `BinaryResult` — the process exit code on completion, -1 on backstop expiry.
- **Errors**: `IOException` from process start when the binary is missing or not executable; `InterruptedException` from an interrupted wait; errors from `getCommandArray()`.

`withConfigurationSnapshot(block: () -> R): R` (internal)
- **Behavior**: Snapshots `allArguments` and `allOptions`, runs `block`, and restores both registries in a finally block — on normal completion and on exception.
- **Input**: `block` — computation run under mutable configuration.
- **Output**: The value returned by `block`.
- **Errors**: Propagates any exception from `block`; state is restored regardless.

### BinPhpParser

**Responsibility**: PHP-specific binary resolver and AST parser that configures and executes the php-parser binary. Public final class, root package.

**State/Fields**:
- `phpBinaryFile: File` (private) — Resolved PHP interpreter (caller-supplied, bundled, or system). Resolved eagerly on construction.
- `parserBinaryFile: File` (private) — Resolved php-parser PHAR. Resolved eagerly on construction.
- `target: File` — (Argument) Target PHP source file to parse. Required before `execute()`.
- `dumpType: DumpType` — (Argument) AST output format. Defaults to `S_EXPR`.
- `doPrettyPrint: Boolean` — (Option `--pretty-print`) Defaults to false.
- `doResolveName: Boolean` — (Option `--resolve-names`) Applies `NodeVisitor\NameResolver`. Defaults to false.
- `doWithColInfo: Boolean` — (Option `--with-column-info`) Defaults to false.
- `doWithPositions: Boolean` — (Option `--with-positions`) Defaults to false.
- `doWithRecovery: Boolean` — (Option `--with-recovery`) Defaults to false.
- `preloadOsUniformer: Map` (private) — OS name normalization map (`mac`→`macos`, `win`→`windows`, `nix`/`nux`/`aix`→`linux`).
- `preloadArchUniformer: Map` (private) — Architecture normalization map (`aarch64`/`arm64`→`aarch64`, `x86_64`/`amd64`→`x86_64`).
- `preloadCrc32CheckSum: Map` (private) — Known-good CRC32 checksums for bundled binaries.

**Inner Types**:

`DumpType` (public enum, data holder)
- `S_EXPR("--dump")`, `VAR("--var-dump")`, `JSON("--json-dump")`
- `opt: String` holds the CLI flag passed to the parser.

**Methods**:

`getCommandArray(): Array<String>` (override)
- **Behavior**: Builds the command array in one pass: PHP binary, parser PHAR, every option whose value is `true`, dump type flag, target path.
- **Input**: Reads `phpBinaryFile`, `parserBinaryFile`, `allOptions`, `dumpType`, `target`.
- **Output**: `Array<String>`.
- **Errors**: `ExternalBinaryArgumentMissException` if `target` not set.

**Construction**:
- PHP binary resolution: caller-supplied (validated `>= MIN_PHP_VERSION`, 7.1) > bundled ZIP extraction for the normalized platform (with CRC32 check) > system PATH search (first `php` on PATH that satisfies the minimum version). Hosts with no bundled variant, or a missing bundled resource, go straight to the PATH search. Throws `ExternalBinaryNotFoundException` when all fail; throws `ExternalBinaryInvalidException` when a caller-supplied interpreter is below the minimum version.
- Parser binary resolution: caller-supplied (not validated) > bundled ZIP extraction (with CRC32 check). Throws `ExternalBinaryNotFoundException` when the bundled resource is absent.
- A pre-existing extraction under `workTmpDir` whose CRC32 matches the preloaded checksum is reused; extraction is skipped.
- An extracted interpreter that cannot be marked executable throws `ExternalBinaryInvalidException`.

### BinaryResult

**Responsibility**: Immutable data holder for an execution outcome. Public data class, `binary` subpackage.

**State/Fields**:
- `code: Int` — Process exit code. 0 = success, -1 = backstop expiry, other values as returned by the process.
- `output: File` — File containing captured stdout+stderr.

---

## Function Specifications

### ScopedExecution.kt

**`executeWith(tmpConfig: T.() -> Unit): BinaryResult`** (public extension on `T : AbcBinary`)
- **Responsibility**: Execute a binary with temporary configuration, restoring original state afterward.
- **Behavior**: Delegates to `AbcBinary.withConfigurationSnapshot`: applies `tmpConfig` and calls `execute()` under the snapshot; the snapshot restores both registries afterward.
- **Input**: `tmpConfig` — configuration lambda applied to the receiver `AbcBinary` subtype.
- **Output**: `BinaryResult` from the temporary execution.
- **Errors**: Propagates any exception from `tmpConfig` or `execute()`. State is always restored — both normal returns and exceptions.

### ExecutableSearch.kt

**`searchBin(under: Path, vararg possibleNames: String): File?`** (public)
- **Responsibility**: Look up an executable as a direct child of a directory.
- **Behavior**: Checks each candidate name as a direct child of `under`, returns the first that is an executable regular file.
- **Input**: `under` — directory; `possibleNames` — candidate file names.
- **Output**: First matching `File`, or null.
- **Errors**: None thrown; returns null on no match.

**`searchBin(name: String): File?`** (public)
- **Responsibility**: Search system PATH for an executable by name.
- **Behavior**: On Windows, a name without an `.exe`/`.bat` suffix is tried as `name.exe` then `name.bat`. Lazily iterates PATH entries, skips non-existent entries, delegates to `searchBin(Path, ...)`. Short-circuits on first match.
- **Input**: `name` — base executable name.
- **Output**: First matching `File`, or null.
- **Errors**: None thrown; returns null if PATH is unset or no match.

### PhpVersionValidation.kt

**`readPhpVersion(binary: File, probeTimeoutSeconds: Long): String`** (internal)
- **Responsibility**: Read the version reported by `php -v`.
- **Behavior**: Spawns `binary -v`, waits up to the probe backstop (`VERSION_PROBE_TIMEOUT_SECONDS`, 10 s), extracts `major.minor.patch` from the first output line via a precompiled regex. A hung probe is force-killed and reaped. Internal so tests can shorten the backstop; production callers use the default.
- **Input**: `binary` — PHP executable; `probeTimeoutSeconds` — liveness backstop, defaults to the constant.
- **Output**: Version string `major.minor.patch`.
- **Errors**: Throws `ExternalBinaryInvalidException` (with the `IOException` as cause when present) when the binary cannot run, the probe times out, or the output carries no version.

**`isPhpVersionValid(binary: File, minRequired: String, includeEqual: Boolean): Boolean`** (public)
- **Responsibility**: Check whether a PHP binary meets a minimum version requirement.
- **Behavior**: Validates `minRequired` format, reads the current version via `readPhpVersion`, compares up to `VERSION_COMPONENT_COUNT` (3) components numerically; missing components count as 0. Equal versions satisfy the check only when `includeEqual` is true.
- **Input**: `binary` — PHP executable; `minRequired` — dotted version string (1–3 numeric components); `includeEqual` — whether equality satisfies the check (default true).
- **Output**: `true` if current version meets requirement.
- **Errors**: Throws `ExternalBinaryInvalidException` if `minRequired` has invalid format or a component of either version exceeds the `Int` range, or propagated from `readPhpVersion`.

### ArchiveExtraction.kt

**`extractFileFromZip(zipInputStream: InputStream, toOutPath: Path, vararg fromZipPath: Path)`** (public)
- **Responsibility**: Extract a single target file from a ZIP archive with atomic publication.
- **Behavior**: Creates parent directories, iterates ZIP entries with path normalization (backslash to forward slash), stages the first matching entry to a unique sibling temp file, then moves it over the destination via `ATOMIC_MOVE` (falling back to `REPLACE_EXISTING` when the filesystem lacks atomic-move support). Concurrent readers only ever observe an absent or complete destination, never a partial one. The staging file is removed and the input stream closed on every exit path.
- **Input**: `zipInputStream` — ZIP stream; `toOutPath` — extraction destination; `fromZipPath` — candidate entry paths within ZIP.
- **Output**: None (Unit); the destination file exists on return.
- **Errors**: Throws `ExternalBinaryNotFoundException` when no entry matches. Propagates I/O exceptions from stream operations.

**`Path.crc32ChecksumString: String?`** (public extension property)
- **Responsibility**: Compute CRC32 checksum of a file for integrity verification.
- **Behavior**: Validates file existence, reads in `CRC_BUFFER_SIZE` (16 KB) chunks, returns 8-character lowercase hex string.
- **Input**: Receiver `Path`.
- **Output**: Hex checksum string, or null if the path does not exist or is not a regular file.
- **Errors**: Propagates I/O exceptions from file reading.

**`File.crc32ChecksumString: String?`** (public extension property)
- **Responsibility**: Convenience delegate to `Path.crc32ChecksumString`.

---

## Exception / Error Types

| Exception | Superclass | Raised When |
|-----------|-----------|-------------|
| `ExternalBinaryNotFoundException(name, under)` | `RuntimeException` | Binary resolution fails — not found in bundled resources or system PATH — during `BinPhpParser` construction; or `extractFileFromZip` finds no matching entry. |
| `ExternalBinaryInvalidException(name, reason, cause)` | `RuntimeException` | A binary exists but fails validation — caller-supplied interpreter below the minimum version, invalid version format, version component beyond `Int` range, unrunnable or hung version probe, unparsable version output, or an extracted interpreter that cannot be marked executable. |
| `ExternalBinaryArgumentMissException(argName)` | `RuntimeException` | A required `Argument` delegate is read before being set. Raised when accessing `target` without assignment. |
