# PHP-Parser AST Node Reference

Complete reference of all AST node types produced by [nikic/php-parser](https://github.com/nikic/PHP-Parser) (v5.x).

Each node serializes to JSON with a `nodeType` string identifier, an `attributes` object, and type-specific subnodes.

---

## JSON Structure

```json
{
    "nodeType": "Stmt_Function",
    "attributes": {
        "startLine": 1, "endLine": 5,
        "startTokenPos": 0, "endTokenPos": 20,
        "startFilePos": 0, "endFilePos": 80,
        "comments": []
    },
    "name": { "nodeType": "Identifier", "name": "foo", "attributes": { ... } },
    "params": [],
    "stmts": []
}
```

### Attributes (all nodes)

| Key | Type | Description |
|-----|------|-------------|
| `startLine` | `int` | Starting line number |
| `endLine` | `int` | Ending line number |
| `startTokenPos` | `int` | Starting token position |
| `endTokenPos` | `int` | Ending token position |
| `startFilePos` | `int` | Starting file byte position |
| `endFilePos` | `int` | Ending file byte position |
| `comments` | `Comment[]` | Attached comments (Comment or Comment_Doc) |

### Comment

```json
{ "nodeType": "Comment_Doc", "text": "/** ... */", "line": 3, "filePos": 7, "tokenPos": 2, "endLine": 3, "endFilePos": 31, "endTokenPos": 2 }
```

---

## Statements

### Declarations

| nodeType | Subnodes |
|----------|----------|
| `Stmt_Namespace` | `name: Name?`, `stmts: Stmt[]` |
| `Stmt_Use` | `uses: UseItem[]`, `type: int` |
| `Stmt_GroupUse` | `prefix: Name`, `uses: UseItem[]`, `type: int` |
| `Stmt_Class` | `flags: int`, `name: Identifier`, `extends: Name?`, `implements: Name[]`, `stmts: Stmt[]`, `attrGroups: AttributeGroup[]`, `namespacedName: Name?` |
| `Stmt_Interface` | `name: Identifier`, `extends: Name[]`, `stmts: Stmt[]`, `attrGroups: AttributeGroup[]`, `namespacedName: Name?` |
| `Stmt_Trait` | `name: Identifier`, `stmts: Stmt[]`, `attrGroups: AttributeGroup[]`, `namespacedName: Name?` |
| `Stmt_Enum` | `name: Identifier`, `scalarType: Identifier?`, `implements: Name[]`, `stmts: Stmt[]`, `attrGroups: AttributeGroup[]`, `namespacedName: Name?` |
| `Stmt_EnumCase` | `name: Identifier`, `expr: Expr?`, `attrGroups: AttributeGroup[]` |
| `Stmt_Function` | `byRef: bool`, `name: Identifier`, `params: Param[]`, `returnType: TypeNode?`, `stmts: Stmt[]`, `attrGroups: AttributeGroup[]`, `namespacedName: Name?` |
| `Stmt_ClassMethod` | `flags: int`, `byRef: bool`, `name: Identifier`, `params: Param[]`, `returnType: TypeNode?`, `stmts: Stmt[]?`, `attrGroups: AttributeGroup[]` |
| `Stmt_Property` | `flags: int`, `props: PropertyItem[]`, `type: TypeNode?`, `attrGroups: AttributeGroup[]`, `hooks: PropertyHook[]` |
| `Stmt_ClassConst` | `flags: int`, `consts: Const[]`, `attrGroups: AttributeGroup[]`, `type: TypeNode?` |
| `Stmt_Const` | `consts: Const[]` |
| `Stmt_TraitUse` | `traits: Name[]`, `adaptations: TraitUseAdaptation[]` |
| `Stmt_Declare` | `declares: DeclareItem[]`, `stmts: Stmt[]?` |

> **TypeNode** = `Identifier | Name | NullableType | UnionType | IntersectionType`

### Control Flow

| nodeType | Subnodes |
|----------|----------|
| `Stmt_If` | `cond: Expr`, `stmts: Stmt[]`, `elseifs: Stmt_ElseIf[]`, `else: Stmt_Else?` |
| `Stmt_ElseIf` | `cond: Expr`, `stmts: Stmt[]` |
| `Stmt_Else` | `stmts: Stmt[]` |
| `Stmt_For` | `init: Expr[]`, `cond: Expr[]`, `loop: Expr[]`, `stmts: Stmt[]` |
| `Stmt_Foreach` | `expr: Expr`, `keyVar: Expr?`, `byRef: bool`, `valueVar: Expr`, `stmts: Stmt[]` |
| `Stmt_While` | `cond: Expr`, `stmts: Stmt[]` |
| `Stmt_Do` | `stmts: Stmt[]`, `cond: Expr` |
| `Stmt_Switch` | `cond: Expr`, `cases: Stmt_Case[]` |
| `Stmt_Case` | `cond: Expr?` (null=default), `stmts: Stmt[]` |
| `Stmt_Break` | `num: Expr?` |
| `Stmt_Continue` | `num: Expr?` |
| `Stmt_Return` | `expr: Expr?` |
| `Stmt_Goto` | `name: Identifier` |
| `Stmt_Label` | `name: Identifier` |

### Exception Handling

| nodeType | Subnodes |
|----------|----------|
| `Stmt_TryCatch` | `stmts: Stmt[]`, `catches: Stmt_Catch[]`, `finally: Stmt_Finally?` |
| `Stmt_Catch` | `types: Name[]`, `var: Expr_Variable?`, `stmts: Stmt[]` |
| `Stmt_Finally` | `stmts: Stmt[]` |
| `Stmt_Throw` | `expr: Expr` |

### Other Statements

| nodeType | Subnodes |
|----------|----------|
| `Stmt_Expression` | `expr: Expr` |
| `Stmt_Echo` | `exprs: Expr[]` |
| `Stmt_Global` | `vars: Expr_Variable[]` |
| `Stmt_Static` | `vars: StaticVar[]` |
| `Stmt_Unset` | `vars: Expr[]` |
| `Stmt_InlineHTML` | `value: string` |
| `Stmt_HaltCompiler` | `remaining: string` |
| `Stmt_Nop` | *(none)* |
| `Stmt_Block` | `stmts: Stmt[]` |

---

## Expressions

### Variables and Access

| nodeType | Subnodes |
|----------|----------|
| `Expr_Variable` | `name: string|Expr` |
| `Expr_ArrayDimFetch` | `var: Expr`, `dim: Expr?` |
| `Expr_PropertyFetch` | `var: Expr`, `name: Identifier|Expr` |
| `Expr_NullsafePropertyFetch` | `var: Expr`, `name: Identifier|Expr` |
| `Expr_StaticPropertyFetch` | `class: Name|Expr`, `name: VarLikeIdentifier|Expr` |
| `Expr_ClassConstFetch` | `class: Name|Expr`, `name: Identifier` |
| `Expr_ConstFetch` | `name: Name` |

### Calls

| nodeType | Subnodes |
|----------|----------|
| `Expr_FuncCall` | `name: Name|Expr`, `args: Arg[]` |
| `Expr_MethodCall` | `var: Expr`, `name: Identifier|Expr`, `args: Arg[]` |
| `Expr_NullsafeMethodCall` | `var: Expr`, `name: Identifier|Expr`, `args: Arg[]` |
| `Expr_StaticCall` | `class: Name|Expr`, `name: Identifier|Expr`, `args: Arg[]` |
| `Expr_New` | `class: Name|Expr|Stmt_Class`, `args: Arg[]` |

### Assignment

All assignment nodes have subnodes: `var: Expr`, `expr: Expr`

| nodeType | Operator |
|----------|----------|
| `Expr_Assign` | `=` |
| `Expr_AssignRef` | `=&` |
| `Expr_AssignOp_Plus` | `+=` |
| `Expr_AssignOp_Minus` | `-=` |
| `Expr_AssignOp_Mul` | `*=` |
| `Expr_AssignOp_Div` | `/=` |
| `Expr_AssignOp_Mod` | `%=` |
| `Expr_AssignOp_Pow` | `**=` |
| `Expr_AssignOp_Concat` | `.=` |
| `Expr_AssignOp_BitwiseAnd` | `&=` |
| `Expr_AssignOp_BitwiseOr` | `|=` |
| `Expr_AssignOp_BitwiseXor` | `^=` |
| `Expr_AssignOp_ShiftLeft` | `<<=` |
| `Expr_AssignOp_ShiftRight` | `>>=` |
| `Expr_AssignOp_Coalesce` | `??=` |

### Binary Operations

All binary op nodes have subnodes: `left: Expr`, `right: Expr`

**Arithmetic**: `Expr_BinaryOp_Plus` (+), `Expr_BinaryOp_Minus` (-), `Expr_BinaryOp_Mul` (*), `Expr_BinaryOp_Div` (/), `Expr_BinaryOp_Mod` (%), `Expr_BinaryOp_Pow` (**)

**String**: `Expr_BinaryOp_Concat` (.)

**Bitwise**: `Expr_BinaryOp_BitwiseAnd` (&), `Expr_BinaryOp_BitwiseOr` (|), `Expr_BinaryOp_BitwiseXor` (^), `Expr_BinaryOp_ShiftLeft` (<<), `Expr_BinaryOp_ShiftRight` (>>)

**Boolean**: `Expr_BinaryOp_BooleanAnd` (&&), `Expr_BinaryOp_BooleanOr` (||), `Expr_BinaryOp_LogicalAnd` (and), `Expr_BinaryOp_LogicalOr` (or), `Expr_BinaryOp_LogicalXor` (xor)

**Comparison**: `Expr_BinaryOp_Equal` (==), `Expr_BinaryOp_NotEqual` (!=), `Expr_BinaryOp_Identical` (===), `Expr_BinaryOp_NotIdentical` (!==), `Expr_BinaryOp_Greater` (>), `Expr_BinaryOp_GreaterOrEqual` (>=), `Expr_BinaryOp_Smaller` (<), `Expr_BinaryOp_SmallerOrEqual` (<=), `Expr_BinaryOp_Spaceship` (<=>)

**Null coalescing**: `Expr_BinaryOp_Coalesce` (??)

### Unary Operations

| nodeType | Subnodes | Operator |
|----------|----------|----------|
| `Expr_UnaryMinus` | `expr: Expr` | `-$a` |
| `Expr_UnaryPlus` | `expr: Expr` | `+$a` |
| `Expr_BitwiseNot` | `expr: Expr` | `~$a` |
| `Expr_BooleanNot` | `expr: Expr` | `!$a` |
| `Expr_ErrorSuppress` | `expr: Expr` | `@$a` |
| `Expr_PreInc` | `var: Expr` | `++$a` |
| `Expr_PreDec` | `var: Expr` | `--$a` |
| `Expr_PostInc` | `var: Expr` | `$a++` |
| `Expr_PostDec` | `var: Expr` | `$a--` |

### Type Casting

All cast nodes have subnode: `expr: Expr`

`Expr_Cast_Int`, `Expr_Cast_Double`, `Expr_Cast_String`, `Expr_Cast_Bool`, `Expr_Cast_Array`, `Expr_Cast_Object`, `Expr_Cast_Unset`

### Array and List

| nodeType | Subnodes |
|----------|----------|
| `Expr_Array` | `items: (ArrayItem|null)[]` |
| `Expr_List` | `items: (ArrayItem|null)[]` |

### Closures

| nodeType | Subnodes |
|----------|----------|
| `Expr_Closure` | `static: bool`, `byRef: bool`, `params: Param[]`, `uses: ClosureUse[]`, `returnType: TypeNode?`, `stmts: Stmt[]`, `attrGroups: AttributeGroup[]` |
| `Expr_ArrowFunction` | `static: bool`, `byRef: bool`, `params: Param[]`, `returnType: TypeNode?`, `expr: Expr`, `attrGroups: AttributeGroup[]` |

### Control Expressions

| nodeType | Subnodes |
|----------|----------|
| `Expr_Ternary` | `cond: Expr`, `if: Expr?`, `else: Expr` |
| `Expr_Match` | `cond: Expr`, `arms: MatchArm[]` |
| `Expr_Throw` | `expr: Expr` |
| `Expr_Yield` | `key: Expr?`, `value: Expr?` |
| `Expr_YieldFrom` | `expr: Expr` |

### Other Expressions

| nodeType | Subnodes |
|----------|----------|
| `Expr_Instanceof` | `expr: Expr`, `class: Name|Expr` |
| `Expr_Clone` | `expr: Expr` |
| `Expr_Empty` | `expr: Expr` |
| `Expr_Eval` | `expr: Expr` |
| `Expr_Exit` | `expr: Expr?` |
| `Expr_Include` | `expr: Expr`, `type: int` |
| `Expr_Isset` | `vars: Expr[]` |
| `Expr_Print` | `expr: Expr` |
| `Expr_ShellExec` | `parts: (Expr|InterpolatedStringPart)[]` |
| `Expr_Error` | *(none)* |

---

## Scalars

| nodeType | Subnodes | Extra attributes |
|----------|----------|------------------|
| `Scalar_Int` | `value: int` | `kind`: 0=decimal, 8=octal, 16=hex, 2=binary |
| `Scalar_Float` | `value: float` | |
| `Scalar_String` | `value: string` | `kind`: 1=single, 2=double, 3=heredoc, 4=nowdoc; `rawValue` for heredoc/nowdoc |
| `Scalar_InterpolatedString` | `parts: (Expr|InterpolatedStringPart)[]` | |

### Magic Constants

All have no subnodes:

`Scalar_MagicConst_Line`, `Scalar_MagicConst_File`, `Scalar_MagicConst_Dir`, `Scalar_MagicConst_Function`, `Scalar_MagicConst_Class`, `Scalar_MagicConst_Method`, `Scalar_MagicConst_Namespace`, `Scalar_MagicConst_Trait`

---

## Name Nodes

| nodeType | Subnodes | Description |
|----------|----------|-------------|
| `Name` | `name: string` | Relative name (`Foo\Bar`) |
| `Name_FullyQualified` | `name: string` | Fully qualified (`\Foo\Bar`) |
| `Name_Relative` | `name: string` | Namespace-relative (`namespace\Foo`) |

---

## Structural Nodes

| nodeType | Subnodes |
|----------|----------|
| `Identifier` | `name: string` |
| `VarLikeIdentifier` | `name: string` |
| `Param` | `type: TypeNode?`, `byRef: bool`, `variadic: bool`, `var: Expr_Variable`, `default: Expr?`, `flags: int`, `attrGroups: AttributeGroup[]` |
| `Arg` | `name: Identifier?`, `value: Expr`, `byRef: bool`, `unpack: bool` |
| `Const` | `name: Identifier`, `value: Expr`, `namespacedName: Name?` |
| `ArrayItem` | `key: Expr?`, `value: Expr`, `byRef: bool`, `unpack: bool` |
| `ClosureUse` | `var: Expr_Variable`, `byRef: bool` |
| `MatchArm` | `conds: Expr[]?` (null=default), `body: Expr` |
| `StaticVar` | `var: Expr_Variable`, `default: Expr?` |
| `DeclareItem` | `key: Identifier`, `value: Expr` |
| `PropertyItem` | `name: VarLikeIdentifier`, `default: Expr?` |
| `PropertyHook` | `name: Identifier`, `body: Expr|Stmt[]?`, `params: Param[]`, `byRef: bool`, `attrGroups: AttributeGroup[]`, `flags: int` |
| `UseItem` | `type: int`, `name: Name`, `alias: Identifier?` |
| `InterpolatedStringPart` | `value: string` (attribute: `rawValue`) |
| `VariadicPlaceholder` | *(none)* |

### Type Nodes

| nodeType | Subnodes |
|----------|----------|
| `NullableType` | `type: Identifier|Name` |
| `UnionType` | `types: (Identifier|Name|IntersectionType)[]` |
| `IntersectionType` | `types: (Identifier|Name)[]` |

### Attribute Nodes

| nodeType | Subnodes |
|----------|----------|
| `Attribute` | `name: Name`, `args: Arg[]` |
| `AttributeGroup` | `attrs: Attribute[]` |

---

## Constants

### Modifier Flags

| Constant | Value | Used by |
|----------|-------|---------|
| `MODIFIER_PUBLIC` | 1 | Class, ClassMethod, Property, ClassConst, Param |
| `MODIFIER_PROTECTED` | 2 | Same |
| `MODIFIER_PRIVATE` | 4 | Same |
| `MODIFIER_STATIC` | 8 | ClassMethod, Property, Closure, ArrowFunction |
| `MODIFIER_ABSTRACT` | 16 | Class, ClassMethod |
| `MODIFIER_FINAL` | 32 | Class, ClassMethod |
| `MODIFIER_READONLY` | 64 | Class (8.2+), Property (8.1+), Param (promoted) |

### Include Types

| Constant | Value | Statement |
|----------|-------|-----------|
| `TYPE_INCLUDE` | 1 | `include` |
| `TYPE_INCLUDE_ONCE` | 2 | `include_once` |
| `TYPE_REQUIRE` | 3 | `require` |
| `TYPE_REQUIRE_ONCE` | 4 | `require_once` |

### Use Types

| Constant | Value | Statement |
|----------|-------|-----------|
| `TYPE_UNKNOWN` | 0 | Unknown |
| `TYPE_NORMAL` | 1 | `use Foo\Bar` |
| `TYPE_FUNCTION` | 2 | `use function foo` |
| `TYPE_CONSTANT` | 3 | `use const FOO` |

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

---

## JSON Examples

### Function Declaration

```json
[
    {
        "nodeType": "Stmt_Function",
        "attributes": {
            "startLine": 4,
            "comments": [
                {
                    "nodeType": "Comment_Doc",
                    "text": "/** @param string $msg */",
                    "line": 3,
                    "filePos": 7,
                    "tokenPos": 2,
                    "endLine": 3,
                    "endFilePos": 31,
                    "endTokenPos": 2
                }
            ],
            "endLine": 6
        },
        "byRef": false,
        "name": {
            "nodeType": "Identifier",
            "attributes": { "startLine": 4, "endLine": 4 },
            "name": "printLine"
        },
        "params": [
            {
                "nodeType": "Param",
                "attributes": { "startLine": 4, "endLine": 4 },
                "type": null,
                "byRef": false,
                "variadic": false,
                "var": {
                    "nodeType": "Expr_Variable",
                    "attributes": { "startLine": 4, "endLine": 4 },
                    "name": "msg"
                },
                "default": null,
                "flags": 0,
                "attrGroups": []
            }
        ],
        "returnType": null,
        "stmts": [
            {
                "nodeType": "Stmt_Echo",
                "attributes": { "startLine": 5, "endLine": 5 },
                "exprs": [
                    {
                        "nodeType": "Expr_Variable",
                        "attributes": { "startLine": 5, "endLine": 5 },
                        "name": "msg"
                    },
                    {
                        "nodeType": "Scalar_String",
                        "attributes": { "startLine": 5, "endLine": 5, "kind": 2, "rawValue": "\"\\n\"" },
                        "value": "\n"
                    }
                ]
            }
        ],
        "attrGroups": [],
        "namespacedName": null
    }
]
```

### Function Call

```json
{
    "nodeType": "Stmt_Expression",
    "attributes": { "startLine": 1, "endLine": 1 },
    "expr": {
        "nodeType": "Expr_FuncCall",
        "attributes": { "startLine": 1, "endLine": 1 },
        "name": {
            "nodeType": "Name",
            "attributes": { "startLine": 1, "endLine": 1 },
            "name": "var_dump"
        },
        "args": [
            {
                "nodeType": "Arg",
                "attributes": { "startLine": 1, "endLine": 1 },
                "name": null,
                "value": {
                    "nodeType": "Expr_Variable",
                    "attributes": { "startLine": 1, "endLine": 1 },
                    "name": "foo"
                },
                "byRef": false,
                "unpack": false
            }
        ]
    }
}
```
