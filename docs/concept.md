# externs-phpparser: Concepts & Terminology

## 1. Context

**Problem Statement**
Static analysis of PHP source code requires parsing files into Abstract Syntax Trees (ASTs). PHP parsers are written in PHP and distributed as PHAR archives, so a JVM-based analysis pipeline cannot invoke them directly. An adapter layer is needed to manage PHP binary discovery, parser binary extraction, process execution, and result capture — shielding downstream consumers from platform-specific details.

**System Role**
externs-phpparser is the PHP parsing adapter in the Cobra analysis pipeline, translating PHP source files into structured AST output consumable by JVM-based analysis modules.

**Data Flow**
- **Inputs:** PHP source file paths (from upstream analysis orchestrator)
- **Outputs:** AST text (S-expression, JSON, or var-dump format) as file references
- **Connections:** [Analysis Orchestrator] → [externs-phpparser] → [AST Consumer]

**Scope Boundaries**
- **Owned:** PHP binary resolution (bundled or system), parser binary extraction, process lifecycle management, output caching, platform normalization (OS/arch), integrity verification (CRC32)
- **Not Owned:** AST interpretation or transformation, PHP installation, upstream orchestration logic, downstream AST consumption

## 2. Concepts

**Conceptual Diagram**
```
+------------------+       +------------------+       +------------------+
| Analysis         |       | externs-phpparser|       | AST Consumer     |
| Orchestrator     | ----> |                  | ----> | (downstream)     |
|                  |       | Binary Resolver  |       |                  |
+------------------+       | Process Executor |       +------------------+
                           | Output Cache     |
                           +------------------+
                                   |
                           +-------+-------+
                           |               |
                    +-----------+   +-----------+
                    | Bundled   |   | System    |
                    | PHP + PHAR|   | PHP (PATH)|
                    +-----------+   +-----------+
```

**Core Concepts**

- **Name:** Binary Resolution
  - **Definition:** Locating a valid PHP interpreter and php-parser PHAR before any parse runs, so that execution never starts against a missing or unusable binary.
  - **Scope:** Includes the priority chain — caller-supplied path, bundled archive extraction with integrity verification, system PATH search — and the minimum-version check on the interpreter. Excludes installing PHP.
  - **Relationships:** Uses Platform Normalization to select the bundled variant and Integrity Verification to decide whether extraction is needed; precedes Process Execution.

- **Name:** Platform Normalization
  - **Definition:** Mapping raw OS and architecture identifiers to the canonical keys used in bundled binary names, so one naming scheme covers heterogeneous hosts.
  - **Scope:** Includes OS names ("Mac OS X" → "macos") and CPU architectures ("arm64" → "aarch64"). Excludes hosts with no bundled variant, which fall through to the system PATH.
  - **Relationships:** Input to Binary Resolution.

- **Name:** Integrity Verification
  - **Definition:** CRC32 checksum comparison of an extracted binary against a preloaded known-good value, so an intact extraction is reused and a corrupted one is replaced.
  - **Scope:** Includes the bundled interpreter and parser PHAR. Excludes caller-supplied binaries.
  - **Relationships:** Gate inside Binary Resolution that skips re-extraction.

- **Name:** Process Execution
  - **Definition:** Spawning the PHP interpreter with a constructed command line, capturing combined stdout/stderr into a file, and bounding the wait with a fixed liveness backstop.
  - **Scope:** Includes command construction, output capture, backstop enforcement, and reaping of a hung process. Excludes interpreting the captured output.
  - **Relationships:** Consumes the binaries chosen by Binary Resolution and the Dump Type; produces a Binary Result; consults the Output Cache first when caching is enabled.

- **Name:** Output Cache
  - **Definition:** Opt-in reuse of previously captured output keyed by command identity, so an identical command is not re-executed.
  - **Scope:** Includes a content hash of the command line as cache key and reuse of successful outputs only. Excludes staleness detection — the source file may change under an unchanged path, so invalidation is the caller's responsibility.
  - **Relationships:** Short-circuits Process Execution on a hit.

- **Name:** Dump Type
  - **Definition:** The output format selector passed to the parser: S-expression (structural, default), JSON (machine-readable), or var-dump (PHP native debug format).
  - **Scope:** Includes the three formats above. Excludes the boolean options (pretty print, name resolution, positions, column info, recovery), which combine with any dump type.
  - **Relationships:** One component of the command line built by Process Execution.

- **Name:** Binary Result
  - **Definition:** The outcome of one execution, pairing an exit code with a reference to the captured output file.
  - **Scope:** Includes exit code 0 (success), the interpreter's own non-zero codes (parse failure), and -1 (backstop expired). The output file always exists and holds AST text, an error message, or a timeout note.
  - **Relationships:** Produced by Process Execution; consumed by the AST Consumer.

- **Name:** Scoped Execution
  - **Definition:** Running one execution under a temporary configuration that is rolled back afterwards, so a one-off override never leaks into later runs.
  - **Scope:** Includes all arguments and options; rollback happens on both normal completion and failure. Excludes the working directory and cache flag.
  - **Relationships:** Wraps Process Execution.

Software structure for these concepts: [design.md](design.md).

## 3. Contracts & Flow

**Data Contracts**
- **With Analysis Orchestrator:** Receives an absolute PHP source file path and optional configuration (dump type, pretty print, name resolution, position info, column info, recovery mode). Returns a Binary Result.
- **With Bundled Resources:** Expects ZIP archives on the classpath named by convention (`php-cli-{version}-{os}-{arch}.zip`, `php-parser-{version}.zip`). Each ZIP contains a single executable at a known internal path.

**Internal Processing Flow**
1. **Binary Resolution** — On construction, resolve the PHP interpreter and parser PHAR: caller-supplied path, then bundled extraction with integrity verification, then system PATH search.
2. **Command Construction** — Assemble the command line: interpreter, parser PHAR, enabled boolean options, dump type flag, target file path.
3. **Cache Check** — When caching is enabled, compute the command's content hash. If a cached output file exists, return it immediately.
4. **Process Spawn** — Start the interpreter with stdout/stderr redirected to a temporary file. Wait up to the fixed liveness backstop.
5. **Result Capture** — On completion, pair the exit code with the output file. On backstop expiry, terminate and reap the process and return a Binary Result with code -1.

## 4. Scenarios

- **Typical:** The orchestrator creates a parser, sets the target file, and executes. The bundled interpreter passes integrity verification (already extracted), the parser runs, and a Binary Result with code 0 and AST output is returned.

- **Boundary:** The bundled interpreter archive is missing from the classpath, and no system PHP at or above the minimum version exists on PATH. Binary Resolution fails on construction — fail-fast, no silent fallback.

- **Boundary:** The parser process hangs. After the fixed one-minute liveness backstop, the process is terminated and reaped, and a Binary Result with code -1 and a timeout note in its output file is returned.

- **Interaction:** The orchestrator uses Scoped Execution to enable JSON output for one call. After execution, all arguments and options are restored, preserving the parser's default configuration for subsequent calls.
