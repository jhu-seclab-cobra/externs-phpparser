# PHP-Parser AST Expression, Scalar, and Name Nodes

Expression (`Expr_*`), scalar (`Scalar_*`), and name nodes as produced by [nikic/php-parser](https://github.com/nikic/PHP-Parser) (v5.x). Serialization format: [php_parser_ast_format.md](php_parser_ast_format.md).

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
