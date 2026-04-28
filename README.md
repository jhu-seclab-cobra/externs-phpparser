# COBRA.EXTERNS.PHPPARSER

> Kotlin/JVM wrapper for nikic/PHP-Parser -- parses PHP source into ASTs.

Parses PHP source files into AST text (S-expression, JSON, or var-dump) by managing a bundled or system PHP binary behind a single `execute()` call. Bundles [PHP-Parser 5.7.0](https://github.com/nikic/PHP-Parser).

[![codecov](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser/branch/main/graph/badge.svg)](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser)
![Kotlin JVM](https://img.shields.io/badge/Kotlin%20JVM-2.0.1%20%7C%20JVM%201.8%2B-blue?logo=kotlin)
[![Release](https://img.shields.io/badge/release-v0.1.0-blue.svg)](https://github.com/jhu-seclab-cobra/externs-phpparser/releases/tag/v0.1.0)
[![last commit](https://img.shields.io/github/last-commit/jhu-seclab-cobra/externs-phpparser)](https://github.com/jhu-seclab-cobra/externs-phpparser/commits/main)
[![](https://jitpack.io/v/jhu-seclab-cobra/externs-phpparser.svg)](https://jitpack.io/#jhu-seclab-cobra/externs-phpparser)
![Repo Size](https://img.shields.io/github/repo-size/jhu-seclab-cobra/externs-phpparser)
[![license](https://img.shields.io/github/license/jhu-seclab-cobra/externs-phpparser)](./LICENSE)

## Install

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.jhu-seclab-cobra:externs-phpparser:0.1.0")
}
```

Bundled PHP platforms (no system PHP needed): macOS x86_64/aarch64, Linux x86_64/aarch64, Windows x86_64. Other platforms require PHP 7.1+ on system PATH.

## Usage

```kotlin
val parser = BinPhpParser().apply {
    dumpType = BinPhpParser.DumpType.JSON  // S_EXPR (default), VAR, JSON
    doPrettyPrint = true
    doResolveName = true
}

parser.target = File("path/to/file.php")
val result = parser.execute()
// result.code: 0 = success, -1 = timeout
// result.output: File containing AST text
```

```kotlin
// One-off config override (restores original state after)
val result = parser.executeWith {
    target = File("path/to/another.php")
    doWithPositions = true
}
```

## API

**`BinPhpParser(phpBinary?, parserBinary?)`** -- extends `AbcBinary`. Properties: `target`, `dumpType` (`S_EXPR`/`VAR`/`JSON`), `doPrettyPrint` (`--pretty-print`), `doResolveName` (`--resolve-names`), `doWithColInfo` (`--with-column-info`), `doWithPositions` (`--with-positions`), `doWithRecovery` (`--with-recovery`).

**`AbcBinary`** -- abstract process runner. `execute(): BinaryResult`, `timeout: Duration` (default 1 min), `doCacheOutput: Boolean`.

**`BinaryResult(code: Int, output: File)`** -- exit code 0 = success, -1 = timeout.

**`executeWith { }`** -- temporary config override, restores state via try-finally.

**`searchBin(name): File?`** -- find executables on system PATH.

**`isPhpVersionValid(binary, minRequired, includeEqual): Boolean`** -- PHP version check.

**Exceptions**: `ExternalBinaryNotFoundException`, `ExternalBinaryInvalidException`, `ExternalBinaryArgumentMissException`.

## Documentation

- [Concepts](docs/idea.md) -- problem context, data flow, core concepts, scenarios
- [Design](docs/design.md) -- class/type specifications, function signatures, validation rules
- [Implementation Notes](docs/impl.md) -- APIs, libraries, developer instructions
- [PHP-Parser AST Reference](docs/php_parser_ast.md) -- all AST node types, subnodes, JSON format

## For Agents

Agent-consumable documentation index at `docs/llms.txt` ([llmstxt.org](https://llmstxt.org) format).

## Citation

```bibtex
@inproceedings{xu2026cobra,
  title     = {CoBrA: Context-, Branch-sensitive Static Analysis for Detecting Taint-style Vulnerabilities in PHP Web Applications},
  author    = {Xu, Yichao and Kang, Mingqing and Thimmaiah, Neil and Gjomemo, Rigel and Venkatakrishnan, V. N. and Cao, Yinzhi},
  booktitle = {Proceedings of the 48th IEEE/ACM International Conference on Software Engineering (ICSE)},
  year      = {2026},
  address   = {Rio de Janeiro, Brazil}
}
```

## License

GPL-2.0-only
