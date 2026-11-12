# externs-phpparser

[![codecov](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser/branch/main/graph/badge.svg)](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser)
![Kotlin JVM](https://img.shields.io/badge/Kotlin%20JVM-2.4.20%20%7C%20JVM%201.8%2B-blue?logo=kotlin)
[![Release](https://img.shields.io/badge/release-v0.1.0-blue.svg)](https://github.com/jhu-seclab-cobra/externs-phpparser/releases/tag/v0.1.0)
[![last commit](https://img.shields.io/github/last-commit/jhu-seclab-cobra/externs-phpparser)](https://github.com/jhu-seclab-cobra/externs-phpparser/commits/main)
[![JitPack](https://jitpack.io/v/jhu-seclab-cobra/externs-phpparser.svg)](https://jitpack.io/#jhu-seclab-cobra/externs-phpparser)
![Repo Size](https://img.shields.io/github/repo-size/jhu-seclab-cobra/externs-phpparser)
[![license](https://img.shields.io/github/license/jhu-seclab-cobra/externs-phpparser)](./LICENSE)

> The name identifies the external PHP parser adapter.

Parses PHP source files into AST text in S-expression, JSON, or var-dump format.

## Install

Requires JDK 8 or later and a Gradle Kotlin/JVM project.

```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies { implementation("com.github.jhu-seclab-cobra:externs-phpparser:0.1.0") }
```

The package bundles [PHP-Parser](https://github.com/nikic/PHP-Parser) and a PHP interpreter for macOS and Linux on x86_64/aarch64, and Windows on x86_64. Other platforms require PHP 7.4 or later on `PATH`.

## Usage

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

[Parser API](docs/llms/parser.md) specifies `BinPhpParser`, its inherited execution API, `BinaryResult`, helpers and exceptions, including parameter types and defaults.

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

Please cite the associated paper when using this library.

## Documentation

- [Concepts](docs/concept.md): responsibilities, inputs, outputs and scenarios.
- [Design](docs/design.md): types, execution contracts and caller examples.
- [AST reference](docs/research/index.md): node types, subnodes and JSON representation.

## For Agents

Agent-consumable documentation index at [docs/llms.txt](docs/llms.txt) (llmstxt.org format).

## License

`GPL-2.0` — see [LICENSE](LICENSE).
