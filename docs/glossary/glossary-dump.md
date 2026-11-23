# Glossary — Dump

The text forms in which the parser returns the AST of one PHP file.

---

## Dumps

### JSON dump
- **Definition:** The compact JSON text of the AST of one PHP file, with each byte `XX` outside UTF-8 escaped as `\udcxx` and an infinite float written as `1e999` or `-1e999`. The writer imposes no nesting limit.
- **Example:** `<?php 1;` → a JSON array holding one `Stmt_Expression` object whose `expr` is a `Scalar_Int` object with `value` 1.
- **Source:** `resources/php_parse.php` `encodeJson`, `writeJson`; `kotlin/edu/jhu/cobra/externs/phpparser/BinPhpParser.kt` `BinPhpParser` `DumpType` `JSON`; `docs/spec.md`
