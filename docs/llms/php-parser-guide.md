# Parser output usage

> Caller requirements for reading parser output and unresolved names.

## Quick Start

```kotlin
import edu.jhu.cobra.externs.phpparser.BinPhpParser
import java.io.File
val parser = BinPhpParser().apply {
    target = File("example.php").absoluteFile
    dumpType = BinPhpParser.DumpType.JSON
}
val result = parser.execute()
println(result.code)
println(result.output.readText())
```

## API

- `BinaryResult.code: Int` is the process exit code; `-1` indicates timeout.
- `BinaryResult.output: File` contains merged stdout and stderr, including diagnostics and operation headers before the payload.
- The [AST reference](../research/index.md) specifies node and subnode representations.

## Configuration

- Set `dumpType = BinPhpParser.DumpType.JSON` to request the [JSON dump](../glossary/glossary-dump.md#json-dump).
- Set `doResolveName = true` to apply PHP-Parser name resolution.
- Set `doWithRecovery = true` to collect parse diagnostics and request a partial AST.
- Leave `doPrettyPrint` false when expecting only an AST payload; enabling it emits PHP text before the requested dump.

## Gotchas

- Skip diagnostic and operation-header lines before decoding the expected payload; output is not a standalone JSON file.
- Recovery can exit `0` with no AST when the parser returns no statements; require a JSON payload before decoding.
- Name resolution leaves `self`, `parent`, `static`, and unqualified function/constant names for caller lookup. Unqualified functions/constants carry a namespace-prefixed `namespacedName` attribute.
- `Name.name` is one backslash-separated string. `Name_FullyQualified` represents resolved names; `Stmt_Namespace` and `Stmt_Use` remain in the output.
- Name resolution resolves namespace-relative names; it does not decide late static binding.
