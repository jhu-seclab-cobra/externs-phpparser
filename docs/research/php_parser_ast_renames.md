# PHP-Parser v5 Node Renames

`nodeType` identifiers renamed between [nikic/php-parser](https://github.com/nikic/PHP-Parser) v4 and v5. Current node tables: [php_parser_ast_statements.md](php_parser_ast_statements.md), [php_parser_ast_expressions.md](php_parser_ast_expressions.md).

---

## v5 Node Renames

| Old nodeType | New nodeType |
|--------------|-------------|
| `Scalar_LNumber` | `Scalar_Int` |
| `Scalar_DNumber` | `Scalar_Float` |
| `Scalar_Encapsed` | `Scalar_InterpolatedString` |
| `Scalar_EncapsedStringPart` | `InterpolatedStringPart` |
| `Expr_ArrayItem` | `ArrayItem` |
| `Expr_ClosureUse` | `ClosureUse` |
| `Stmt_DeclareDeclare` | `DeclareItem` |
| `Stmt_PropertyProperty` | `PropertyItem` |
| `Stmt_StaticVar` | `StaticVar` |
| `Stmt_UseUse` | `UseItem` |
