# COBRA.EXTERNS.PHPPARSER

> Named for the external binary it wraps — the PHP-Parser library by nikic.

Kotlin/JVM wrapper for [PHP-Parser 5.7.0](https://github.com/nikic/PHP-Parser). Parses PHP source files into AST text (S-expression, JSON, or var-dump) by managing a bundled or system PHP binary behind a single `execute()` call.

[![codecov](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser/branch/main/graph/badge.svg)](https://codecov.io/gh/jhu-seclab-cobra/extern.phpparser)
![Kotlin JVM](https://img.shields.io/badge/Kotlin%20JVM-2.0.1%20%7C%20JVM%201.8%2B-blue?logo=kotlin)
[![Release](https://img.shields.io/badge/release-v0.1.0-blue.svg)](https://github.com/jhu-seclab-cobra/externs-phpparser/releases/tag/v0.1.0)
[![last commit](https://img.shields.io/github/last-commit/jhu-seclab-cobra/externs-phpparser)](https://github.com/jhu-seclab-cobra/externs-phpparser/commits/main)
[![](https://jitpack.io/v/jhu-seclab-cobra/externs-phpparser.svg)](https://jitpack.io/#jhu-seclab-cobra/externs-phpparser)
![Repo Size](https://img.shields.io/github/repo-size/jhu-seclab-cobra/externs-phpparser)
[![license](https://img.shields.io/github/license/jhu-seclab-cobra/externs-phpparser)](./LICENSE)

## Install

Add JitPack repository and dependency to `build.gradle.kts`:

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
// one-off config override (restores original state after)
val result = parser.executeWith {
    target = File("path/to/another.php")
    doWithPositions = true
}
```

## API

**`BinPhpParser`** extends `AbcBinary`. Properties: `target: File`, `dumpType: DumpType` (`S_EXPR`/`VAR`/`JSON`), `doPrettyPrint`, `doResolveName`, `doWithColInfo`, `doWithPositions`, `doWithRecovery` (all default `false`).

**`AbcBinary`** — abstract process execution framework. `execute(): BinaryResult`, `timeout: Duration` (default 1 min), `doCacheOutput: Boolean` (default `false`).

**`BinaryResult(code: Int, output: File)`** — exit code 0 = success, -1 = timeout.

**`executeWith { }`** — temporary config override, restores state after execution.

**`searchBin(name: String): File?`** — find executables on system PATH.

**`isPhpVersionValid(binary, minRequired, includeEqual): Boolean`** — version check.

**Exceptions**: `ExternalBinaryNotFoundException`, `ExternalBinaryInvalidException`, `ExternalBinaryArgumentMissException`.

## Documentation

- [Concepts & Terminology](docs/idea.md) — problem context, data flow, core concepts, scenarios
- [Design](docs/design.md) — class/type specifications, function signatures, exceptions, validation rules
- [Implementation Notes](docs/impl.md) — APIs, libraries, developer instructions
- [PHP-Parser AST Node Reference](docs/php_parser_ast.md) — all AST node types, subnodes, JSON format, constants

## For Agents

Agent-consumable documentation index at `docs/llms.txt` (llmstxt.org format).

## Citation

If you use this repository in your research, please cite our paper:

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
