# externs-phpparser Implementation Notes

## APIs

- **[php-parser]** `(new ParserFactory())->createForHostVersion()` — create parser for current PHP version.
- **[php-parser]** `json_encode($stmts, JSON_PRETTY_PRINT)` — all AST nodes implement `JsonSerializable`; produces JSON with `nodeType` + `attributes` + subnodes.
- **[php-parser]** `Node::getType()` — returns `nodeType` string: class name without `PhpParser\Node\` prefix, `\` → `_`, no trailing `_` for reserved keywords (e.g. `Scalar\Int_` → `Scalar_Int`).
- **[java.nio.file]** `Files.move(stage, target, ATOMIC_MOVE)` — throws `AtomicMoveNotSupportedException` on filesystems without atomic rename; retry with `REPLACE_EXISTING`.
- **[java.lang]** `Process.waitFor(timeout, unit)` — returns `false` on expiry without terminating the process; call `destroy()`/`destroyForcibly()` then `waitFor()` to reap.
- **[java.lang]** `ProcessBuilder.redirectErrorStream(true).redirectOutput(file)` — merges stderr into the stdout file; `inputStream` is then empty.
- **[kotlin.io.path]** `createTempFile(dir, prefix, suffix)` — unique sibling file in `dir`; used for both the extraction stage file and per-execution output.
- **[java.security]** `MessageDigest.getInstance("SHA-1")` — cache key digest over the NUL-joined command array; no collision on argument boundaries.

## Libraries

- `nikic/php-parser` v5.7.0 — PHP AST parser. Bundled as PHAR (`php-parser-5.7.0.zip` classpath resource), invoked via `BinPhpParser`.
- `php-cli` 8.4 — interpreter bundled as `php-cli-8.4-{os}-{arch}.zip` classpath resources for macos/linux (x86_64, aarch64) and windows (x86_64).
- `org.junit.jupiter:junit-jupiter-api:5.14.4`, `org.junit.jupiter:junit-jupiter-params:5.14.4` — test framework. Aliases `libs.junit.jupiter.api`, `libs.junit.jupiter.params`.
- `io.mockk:mockk:1.14.11` — test doubles at process boundaries. Alias `libs.mockk`.

## Developer Instructions

- Toolchain: JDK 8 (foojay-resolved), Kotlin 2.4.20, ktlint 1.8.0 (plugin 14.2.0), detekt 1.23.8 with `config/detekt/detekt.yml`.
- Quality gate: `./gradlew detekt ktlintCheck build -q`; auto-format with `./gradlew ktlintFormat -q`.
- Performance benchmarks are tagged `performance` and excluded from `test`; run `./gradlew performanceTest`.
- Bundled binary checksums (`preloadCrc32CheckSum`) must be updated whenever a resource zip is replaced.
- AST node reference (all nodeTypes, subnodes, constants, JSON format): [research/index.md](research/index.md).
- JSON output: top-level is `Stmt[]` array. Each node has `"nodeType"` string + `"attributes"` object + subnode keys.
- `attributes` always contains: `startLine`, `endLine`, `startTokenPos`, `endTokenPos`, `startFilePos`, `endFilePos`, `comments`.
- `flags` is a bitmask: combine with `|`, test with `&`. Values: PUBLIC=1, PROTECTED=2, PRIVATE=4, STATIC=8, ABSTRACT=16, FINAL=32, READONLY=64.
- Nullable subnodes serialize as JSON `null`. Array subnodes serialize as JSON arrays (may contain `null` elements, e.g. `Expr_Array.items`).
- v5 renamed several nodes (e.g. `LNumber`→`Int_`, `ArrayItem` no longer under `Expr_`). Rename table: [php_parser_ast_renames.md](research/php_parser_ast_renames.md).
- `Name.name` is a single string (`"Foo\Bar"`), not a `parts` array. Use `getParts()` for array form.

## Design-specific

### BinPhpParser

- `DumpType.JSON` (`--json-dump`) yields the JSON AST consumed downstream.
- Parser output file (`BinaryResult.output`) contains raw JSON prepended with `====> File ...` header lines. Downstream parser must skip non-JSON prefix.
- Error recovery mode (`--with-recovery`): parser inserts `Expr_Error` placeholder nodes instead of throwing.
- Output cache keys on the command line, not file content; a changed file under the same path is a cache hit.

### NameResolver

- `--resolve-names` applies `NodeVisitor\NameResolver`.
- After resolution two Name nodeTypes appear: `Name_FullyQualified` (resolved) and `Name` (unresolved: `self`, `parent`, `static`, unqualified functions/constants). `Name_Relative` is fully resolved.
- `Stmt_Namespace` and `Stmt_Use` remain in the AST.
- `self`/`parent` → downstream Phase 0 replaces with current/parent class FQN (as Zend's `CG(active_class_entry)`).
- `static` → cannot resolve statically (runtime late binding).
- Unqualified functions/constants → downstream Phase 1 checks the `namespacedName` attribute first, then global fallback.
- Full resolution table and AST format details: [php-parser-guide.md](llms/php-parser-guide.md).

### ConstExprEvaluator

- `PhpParser\ConstExprEvaluator` — library class, not exposed via CLI. Evaluates constant expressions (`1+2` → `3`, string concat, array literals).
- Requires custom fallback for `ConstFetch`, `ClassConstFetch`, and magic constants. Not integrated into the `BinPhpParser` CLI pipeline.
