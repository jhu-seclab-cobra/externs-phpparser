# externs-phpparser: JSON Dump Writer

The algorithm the Dump Script runs for `--json-dump` ([concept.md](concept.md) Dump Script). It writes the same JSON text as `json_encode($stmts, JSON_INVALID_UTF8_SUBSTITUTE | JSON_THROW_ON_ERROR)` would with no depth limit, but walks the AST with an explicit stack, so the nesting depth is bounded by the interpreter's memory alone, never by `json_encode`'s depth limit or the native stack (user decision PA4).

## Input and Output

- **Input:** the array of top-level statements returned by the parser, after the requested operations ran.
- **Output:** one compact JSON text (no whitespace between tokens) on success; on failure, no JSON, a message on stderr, and exit code 1.

## Value Kinds

Every value met during the walk is resolved to one of three kinds before it is written:

1. **Object implementing `JsonSerializable`** (every php-parser node and comment): replaced by the value its `jsonSerialize()` returns, and resolved again. `jsonSerialize()` returns one level only; the children stay unexpanded objects.
2. **Container:** a PHP array. It is a JSON array when its keys are exactly `0, 1, …, n-1` in order, the empty array included; otherwise a JSON object whose member names are the keys as strings. This is `json_encode`'s rule.
3. **Leaf:** `null`, a boolean, an integer, a float, or a string. It is written as `json_encode` writes it with the two flags above, so string escaping, U+FFFD substitution and float formatting are `json_encode`'s own.

Any other object fails the dump: php-parser emits none.

## Steps

1. Resolve the input; it is a container. Write its opening bracket and push a frame holding its keys, its values, whether it is a JSON array, and a cursor at 0.
2. While the stack is not empty, take the top frame.
   1. If its cursor is past its last value, write its closing bracket, pop it, and continue.
   2. If its cursor is not 0, write `,`. If it is a JSON object, write the key as a JSON string and `:`.
   3. Resolve the value at the cursor and advance the cursor.
   4. A container: write its opening bracket and push its frame. A leaf: write it.
3. Print the written text. A leaf `json_encode` rejects (a float that is infinite or NaN) throws `JsonException`, which ends the run as an encoding failure.

## Termination

Each step either writes one value or closes one frame. Every value belongs to exactly one container, since the AST is a tree, so each value is resolved once and each frame is pushed and popped once.

## Edge Cases

- `<?php $x = 1 . 1 . … . 1;` with 5000 `. 1` terms: PHP runs it. `json_encode` at its largest depth, `2147483647`, still fails ("Maximum stack depth exceeded") at about 3250 levels on an 8 MB stack. The writer emits all 5001 `Scalar_Int` nodes.
- `[]` from a node with no statements: written as `[]`, as `json_encode` does.
