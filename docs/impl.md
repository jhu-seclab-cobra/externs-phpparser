# php-parser Implementation Notes

## Libraries

- `nikic/php-parser` v5.x — PHP AST parser. Bundled as PHAR, invoked via `BinPhpParser`.

## APIs

- **[ParserFactory]** `(new ParserFactory())->createForHostVersion()` — create parser for current PHP version.
- **[json_encode]** `json_encode($stmts, JSON_PRETTY_PRINT)` — all AST nodes implement `JsonSerializable`; produces JSON with `nodeType` + `attributes` + subnodes.
- **[Node::getType()]** returns `nodeType` string: class name without `PhpParser\Node\` prefix, `\` → `_`, no trailing `_` for reserved keywords (e.g. `Scalar\Int_` → `Scalar_Int`).

## Developer Instructions

- AST node reference (all nodeTypes, subnodes, constants, JSON format): see [php_parser_ast.md](php_parser_ast.md).
- JSON output: top-level is `Stmt[]` array. Each node has `"nodeType"` string + `"attributes"` object + subnode keys.
- `attributes` always contains: `startLine`, `endLine`, `startTokenPos`, `endTokenPos`, `startFilePos`, `endFilePos`, `comments`.
- `flags` is a bitmask: combine with `|`, test with `&`. Values: PUBLIC=1, PROTECTED=2, PRIVATE=4, STATIC=8, ABSTRACT=16, FINAL=32, READONLY=64.
- Nullable subnodes serialize as JSON `null`. Array subnodes serialize as JSON arrays (may contain `null` elements, e.g. `Expr_Array.items`).
- v5 renamed several nodes (e.g. `LNumber`→`Int_`, `ArrayItem` no longer under `Expr_`). See rename table in [php_parser_ast.md](php_parser_ast.md#v5-node-renames).
- `Name.name` is now a single string (`"Foo\Bar"`), not `parts` array. Use `getParts()` for array form.

## Design-specific

- `BinPhpParser` uses `DumpType.JSON` (`--json-dump`) to get JSON AST output for downstream parsing.
- Parser output file (`BinaryResult.output`) contains the raw JSON array of `Stmt` nodes.
- Error recovery mode (`--with-recovery`): parser inserts `Expr_Error` placeholder nodes instead of throwing.
