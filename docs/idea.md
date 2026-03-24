# externs-phpparser: Concepts & Terminology

## 1. Context

**Problem Statement**
Static analysis of PHP source code requires parsing files into Abstract Syntax Trees (ASTs). PHP parsers are written in PHP and distributed as PHAR archives, so a JVM-based analysis pipeline cannot invoke them directly. A bridging layer is needed to manage PHP binary discovery, parser binary extraction, process execution, and result capture — shielding downstream consumers from platform-specific details.

**System Role**
externs-phpparser is the PHP parsing adapter in the Cobra analysis pipeline, translating PHP source files into structured AST output consumable by JVM-based analysis modules.

**Data Flow**
- **Inputs:** PHP source file paths (from upstream analysis orchestrator)
- **Outputs:** AST text (S-expression, JSON, or var-dump format) as file references
- **Connections:** [Analysis Orchestrator] -> [externs-phpparser] -> [AST Consumer]

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

- **Binary Resolution:** The process of locating a valid PHP interpreter and php-parser PHAR. Resolution follows a priority chain: user-provided path, bundled ZIP extraction with CRC32 verification, system PATH search. This concept encompasses platform normalization (OS name and CPU architecture mapping) to select the correct bundled binary variant.

- **Platform Normalization:** Mapping raw OS and architecture identifiers to canonical forms. Raw values like "Mac OS X" or "aarch64" are reduced to uniform keys ("macos", "aarch64") used in bundled binary file naming. This ensures consistent binary selection across heterogeneous environments.

- **Integrity Verification:** CRC32 checksum comparison against a preloaded map of known-good values. If an extracted binary's checksum matches, it is reused without re-extraction. This concept prevents both redundant I/O and use of corrupted binaries.

- **Process Execution:** Spawning an external PHP process with a constructed command array, capturing stdout/stderr to a temporary file, and enforcing a timeout. The execution model treats the PHP parser as a stateless function: input file in, AST text out. Timeout enforcement destroys hung processes and returns a sentinel result.

- **Output Caching:** Optional reuse of previously captured output based on command identity. When enabled, a content-hash of the command array serves as a cache key. If a cached output file exists, process spawning is skipped entirely. Caching is opt-in because AST output depends on both the parser and the source file, and staleness detection is the caller's responsibility.

- **Dump Type:** The output format selector for the parser. Three formats are supported: S-expression (structural, default), JSON (machine-readable), and var-dump (PHP native debug format). The dump type is passed as a CLI flag to the php-parser binary.

- **Binary Result:** The outcome of a single execution, pairing an exit code with a reference to the output file. Exit code 0 indicates success; -1 indicates timeout. The output file always exists and contains either AST text or an error/timeout message.

## 3. Contracts & Flow

**Data Contracts**
- **With Analysis Orchestrator:** Receives a PHP source file path (absolute `File`) and optional configuration (dump type, pretty print, name resolution, position info, recovery mode). Returns a `BinaryResult` containing exit code and output file path.
- **With Bundled Resources:** Expects ZIP archives on the classpath named by convention (`php-cli-{version}-{os}-{arch}.zip`, `php-parser-{version}.zip`). Each ZIP contains a single executable at a known internal path.

**Internal Processing Flow**
1. **Binary Resolution** - On construction, resolve PHP interpreter and parser PHAR. Check user-provided path, then attempt bundled extraction with CRC32 validation, then fall back to system PATH search.
2. **Command Construction** - Assemble the command array: PHP binary path, parser PHAR path, enabled boolean options, dump type flag, target file path.
3. **Cache Check** - If caching is enabled, compute content hash of command array. If a cached output file exists, return it immediately.
4. **Process Spawn** - Start the PHP process with stdout/stderr redirected to a temp file. Wait up to the configured timeout.
5. **Result Capture** - On completion, wrap exit code and output file into a `BinaryResult`. On timeout, destroy the process and return a sentinel result.

## 4. Scenarios

- **Typical:** The orchestrator creates a `BinPhpParser`, sets `target` to a PHP file, and calls `execute()`. The bundled PHP binary passes CRC32 check (already extracted), the parser runs, and a `BinaryResult` with code 0 and AST output is returned.

- **Boundary:** The bundled PHP binary ZIP is missing from classpath resources, and no system PHP >= 7.1 exists on PATH. Binary resolution throws `ExternalBinaryNotFoundException` immediately on construction — fail-fast, no silent fallback.

- **Boundary:** The parser process hangs (infinite loop in PHP code). After the configured timeout (default 1 minute), the process is destroyed and a `BinaryResult` with code -1 and a "timed out" message file is returned.

- **Interaction:** The orchestrator uses `executeWith` to temporarily override options (e.g., enable `--json-dump` for one call). After execution, all arguments and options are restored to their previous values, preserving the parser's default configuration for subsequent calls.
