# PHP-Parser AST JSON Format

JSON serialization of AST nodes produced by [nikic/php-parser](https://github.com/nikic/PHP-Parser) (v5.x).

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
