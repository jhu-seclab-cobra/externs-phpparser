# Glossary — Dump

The text forms in which the parser returns the AST of one PHP file.

---

## Dumps

### JSON dump
- **Definition:** The compact JSON text of the AST of one PHP file, equal to what `json_encode` writes for the parsed statements with invalid UTF-8 replaced by U+FFFD, except that no depth limit applies and an infinite float is written as `1e999` or `-1e999`.
- **Example:** `<?php 1;` → a JSON array holding one `Stmt_Expression` object whose `expr` is a `Scalar_Int` object with `value` 1.
- **Source:** `resources/php_parse.php` `encodeJson`, `writeJson`; `kotlin/edu/jhu/cobra/externs/phpparser/BinPhpParser.kt` `BinPhpParser` `DumpType` `JSON`; `docs/spec.md`
