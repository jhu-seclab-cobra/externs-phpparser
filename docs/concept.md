# externs-phpparser — PHP parsing adapter

## Context

JVM callers need the AST of a PHP source file without executing the source. This adapter invokes the PHP-Parser PHAR through a PHP interpreter and returns its captured output.

**System Role:** The adapter connects a JVM caller to the external parser process.

**Data Flow**

- **Inputs:** A PHP source file path and output options.
- **Outputs:** An exit code and a file containing parser output or diagnostics.
- **Connections:** Caller → adapter → PHP-Parser → caller.

**Scope Boundaries**

- **Owned:** Interpreter discovery, resource extraction and validation, process execution, output caching and AST serialization.
- **Not Owned:** PHP installation, AST interpretation, declaration lookup or execution of the parsed program.

## Concepts

```text
Caller → parsing adapter → PHP interpreter + PHP-Parser
Caller ← exit code and output file ← parser process
```

- **Name:** [JSON dump](glossary/glossary-dump.md#json-dump).
- **Role:** The JSON dump lets a caller decode the AST without running the parsed source.
- **Scope:** The AST of one file; source offsets refer to its unchanged bytes.
- **Relationships:** PHP-Parser produces the AST; the adapter serializes it; the caller interprets it.

## Contracts & Flow

**With the caller:** A parse takes one source file and returns an execution result. Output formats, interpreter selection and error behavior are specified in [design](design.md); the JSON writer is specified in [spec](spec.md).

**Internal Processing Flow:** The adapter resolves its interpreter and parser resources, executes one parse, and returns the output file. The caller resolves names the parser leaves unresolved using its own declaration context.

## Scenarios

- **Typical:** A caller parses a valid file and reads the AST from the output file.
- **Boundary:** No bundled interpreter is available and the first executable named `php` on PATH is below PHP 7.4: construction fails rather than searching later PATH entries.
- **Boundary:** A source literal contains an invalid UTF-8 byte: the JSON dump substitutes U+FFFD while offsets continue to index the original source.
- **Boundary:** A deeply nested concatenation produces deeply nested AST nodes: the JSON writer uses no fixed nesting-depth limit, subject to process time and available memory.
- **Interaction:** A caller uses `executeWith` for a single parse; its target and parser flags are restored on every exit, while inherited execution settings retain assignments made by the caller.

## Rationale and Rejected Alternatives

- **Choice:** An explicit stack writes JSON containers so AST depth does not consume the native JSON encoder stack.
- **Rejected:** Increasing `json_encode`'s depth argument does not remove its native-stack constraint.
