# PHP-Parser AST Constants

Integer constants that appear in `flags` and `type` subnodes of [nikic/php-parser](https://github.com/nikic/PHP-Parser) (v5.x) nodes. Node tables: [php_parser_ast_statements.md](php_parser_ast_statements.md), [php_parser_ast_expressions.md](php_parser_ast_expressions.md).

---

## Modifier Flags

| Constant | Value | Used by |
|----------|-------|---------|
| `MODIFIER_PUBLIC` | 1 | Class, ClassMethod, Property, ClassConst, Param |
| `MODIFIER_PROTECTED` | 2 | Same |
| `MODIFIER_PRIVATE` | 4 | Same |
| `MODIFIER_STATIC` | 8 | ClassMethod, Property, Closure, ArrowFunction |
| `MODIFIER_ABSTRACT` | 16 | Class, ClassMethod |
| `MODIFIER_FINAL` | 32 | Class, ClassMethod |
| `MODIFIER_READONLY` | 64 | Class (8.2+), Property (8.1+), Param (promoted) |

## Include Types

| Constant | Value | Statement |
|----------|-------|-----------|
| `TYPE_INCLUDE` | 1 | `include` |
| `TYPE_INCLUDE_ONCE` | 2 | `include_once` |
| `TYPE_REQUIRE` | 3 | `require` |
| `TYPE_REQUIRE_ONCE` | 4 | `require_once` |

## Use Types

| Constant | Value | Statement |
|----------|-------|-----------|
| `TYPE_UNKNOWN` | 0 | Unknown |
| `TYPE_NORMAL` | 1 | `use Foo\Bar` |
| `TYPE_FUNCTION` | 2 | `use function foo` |
| `TYPE_CONSTANT` | 3 | `use const FOO` |
