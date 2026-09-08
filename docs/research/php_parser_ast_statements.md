# PHP-Parser AST Statement and Structural Nodes

Statement (`Stmt_*`) nodes and the non-expression structural nodes they contain, as produced by [nikic/php-parser](https://github.com/nikic/PHP-Parser) (v5.x). Serialization format: [php_parser_ast_format.md](php_parser_ast_format.md).

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
