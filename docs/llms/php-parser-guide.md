# PHP-Parser Integration Guide

> How to parse PHP source code into AST via BinPhpParser, and what the output contains.

## Quick Start

```kotlin
val parser = BinPhpParser().apply {
    dumpType = BinPhpParser.DumpType.JSON
    doResolveName = true              // resolve use/namespace names to FQN
}
parser.target = File("src/Example.php")
val result = parser.execute()
val json = result.output.readText()   // JSON AST array
```

## Parsing Pipeline

```
PHP source file
  → BinPhpParser (Kotlin wrapper)
    → php-cli binary (bundled or system)
      → php-parser.phar (nikic/PHP-Parser v5.7.0)
        → Lexer → Parser → AST
        → NameResolver visitor (if --resolve-names)
        → JSON serializer (if --json-dump)
  → BinaryResult(code, output: File)
    → JSON text: array of Stmt nodes
```

## Recommended Configuration for Static Analysis

```kotlin
val parser = BinPhpParser().apply {
    dumpType = BinPhpParser.DumpType.JSON   // machine-readable
    doResolveName = true                     // FQN for all class/interface refs
    doWithRecovery = true                    // parse broken PHP gracefully
}
```

## JSON Output Format

Top-level: JSON array of statement nodes. Each node:

```json
{
    "nodeType": "Stmt_Class",
    "attributes": {
        "startLine": 3, "endLine": 10,
        "startTokenPos": 5, "endTokenPos": 40,
        "startFilePos": 20, "endFilePos": 150
    },
    "name": { "nodeType": "Identifier", "name": "User" },
    "extends": { "nodeType": "Name_FullyQualified", "name": "App\\Models\\Base" },
    "stmts": [ ... ],
    "namespacedName": { "nodeType": "Name", "name": "App\\Models\\User" }
}
```

Output file starts with header lines (`====> File ...`, `==> Resolved names.`). Skip lines before the `[` when parsing JSON.

## Name Resolution Behavior

With `doResolveName = true`, PHP-Parser applies `NodeVisitor\NameResolver`:

### Resolved to `Name_FullyQualified`

| PHP Code | AST Name | Resolved To |
|----------|---------|-------------|
| `use App\Models\User; new User()` | `Name_FullyQualified` | `App\Models\User` |
| `use App\Foo as F; new F()` | `Name_FullyQualified` | `App\Foo` |
| `extends Foo` in `namespace App` | `Name_FullyQualified` | `App\Foo` |
| `new namespace\Sub()` in `namespace App` | `Name_FullyQualified` | `App\Sub` |
| `use App\{A, B}; new A()` | `Name_FullyQualified` | `App\A` |
| `use function App\helper; helper()` | `Name_FullyQualified` | `App\helper` |

### Left as `Name` (unresolved)

| PHP Code | AST Name | Why | Who Resolves |
|----------|---------|-----|-------------|
| `self::method()` | `Name("self")` | Class-context keyword | Phase 0: replace with current class FQN |
| `parent::method()` | `Name("parent")` | Needs extends resolution | Phase 0: replace with parent class FQN |
| `static::method()` | `Name("static")` | Runtime late binding | Cannot resolve statically |
| `strlen()` in namespace | `Name("strlen")` | Runtime fallback | Phase 1: check namespace, fallback global |
| `PHP_INT_MAX` in namespace | `Name("PHP_INT_MAX")` | Runtime fallback | Phase 1: check namespace, fallback global |

Unqualified functions/constants get a `namespacedName` attribute with the namespace-prefixed version (e.g., `App\strlen`).

### Structural Nodes

`Stmt_Namespace` and `Stmt_Use` remain in the AST after resolution. They are informational — all resolved references use `Name_FullyQualified` directly.

## AST Node Types (Key Categories)

### Statements

| nodeType | PHP | Key Subnodes |
|----------|-----|-------------|
| `Stmt_Namespace` | `namespace App;` | `name`, `stmts` |
| `Stmt_Use` | `use App\Foo;` | `uses: UseItem[]` |
| `Stmt_Class` | `class Foo {}` | `name`, `extends`, `implements`, `stmts`, `namespacedName` |
| `Stmt_Interface` | `interface IFoo {}` | `name`, `extends`, `stmts` |
| `Stmt_Trait` | `trait MyTrait {}` | `name`, `stmts` |
| `Stmt_Enum` | `enum Color {}` | `name`, `stmts`, `scalarType` |
| `Stmt_Function` | `function foo() {}` | `name`, `params`, `stmts`, `returnType` |
| `Stmt_ClassMethod` | method in class | `name`, `params`, `stmts`, `flags`, `returnType` |
| `Stmt_Property` | `public $x;` | `props: PropertyItem[]`, `flags`, `type` |
| `Stmt_ClassConst` | `const X = 1;` | `consts`, `flags` |
| `Stmt_If` | `if () {}` | `cond`, `stmts`, `elseifs`, `else` |
| `Stmt_Return` | `return $x;` | `expr` |

### Expressions

| nodeType | PHP | Key Subnodes |
|----------|-----|-------------|
| `Expr_Variable` | `$x` | `name` |
| `Expr_Assign` | `$x = 1` | `var`, `expr` |
| `Expr_FuncCall` | `foo()` | `name`, `args` |
| `Expr_MethodCall` | `$obj->m()` | `var`, `name`, `args` |
| `Expr_StaticCall` | `Cls::m()` | `class`, `name`, `args` |
| `Expr_New` | `new Foo()` | `class`, `args` |
| `Expr_PropertyFetch` | `$obj->p` | `var`, `name` |
| `Expr_ArrayDimFetch` | `$a["k"]` | `var`, `dim` |
| `Expr_Array` | `[1, 2]` | `items: ArrayItem[]` |

### Names

| nodeType | Meaning |
|----------|---------|
| `Name` | Unresolved name (self, parent, strlen) |
| `Name_FullyQualified` | Resolved FQN (\App\Models\User) |
| `Identifier` | Simple identifier (method name, property name) |

### Scalars (v5 names)

| nodeType | PHP | v4 Name |
|----------|-----|---------|
| `Scalar_Int` | `42` | `Scalar_LNumber` |
| `Scalar_Float` | `3.14` | `Scalar_DNumber` |
| `Scalar_String` | `"hello"` | same |
| `Scalar_InterpolatedString` | `"Hello $name"` | `Scalar_Encapsed` |

### Modifier Flags

Stored in `flags` field as bitmask:

| Flag | Value | PHP |
|------|-------|-----|
| PUBLIC | 1 | `public` |
| PROTECTED | 2 | `protected` |
| PRIVATE | 4 | `private` |
| STATIC | 8 | `static` |
| ABSTRACT | 16 | `abstract` |
| FINAL | 32 | `final` |
| READONLY | 64 | `readonly` |

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Valid PHP | `BinaryResult(code=0, output=jsonFile)` |
| Syntax error, no recovery | `BinaryResult(code=1, output=errorFile)` |
| Syntax error, with recovery | `BinaryResult(code=0, output=partialAst)` — `Expr_Error` nodes inserted |
| Timeout | `BinaryResult(code=-1, output=timeoutFile)` |
| No PHP binary | `ExternalBinaryNotFoundException` at construction |
