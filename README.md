# COBRA.EXTERNS.PHPPARSER

[![codecov](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser/branch/main/graph/badge.svg)](https://codecov.io/gh/jhu-seclab-cobra/extern.phpparser)
![Kotlin JVM](https://img.shields.io/badge/Kotlin%20JVM-2.0.1%20%7C%20JVM%201.8%2B-blue?logo=kotlin)
[![Release](https://img.shields.io/badge/release-v0.1.0-blue.svg)](https://github.com/jhu-seclab-cobra/externs-phpparser/releases/tag/v0.1.0)
[![last commit](https://img.shields.io/github/last-commit/jhu-seclab-cobra/externs-phpparser)](https://github.com/jhu-seclab-cobra/externs-phpparser/commits/main)
[![](https://jitpack.io/v/jhu-seclab-cobra/externs-phpparser.svg)](https://jitpack.io/#jhu-seclab-cobra/externs-phpparser)
![Repo Size](https://img.shields.io/github/repo-size/jhu-seclab-cobra/externs-phpparser)
[![license](https://img.shields.io/github/license/jhu-seclab-cobra/externs-phpparser)](./LICENSE)

Kotlin/JVM wrapper for [PHP-Parser 4.19.4](https://github.com/nikic/PHP-Parser). Parses PHP source files into AST text (S-expression, JSON, or var-dump) by managing a bundled or system PHP binary behind a single `execute()` call.

## Scope

| Owned | Not Owned |
|-------|-----------|
| PHP binary resolution (bundled ZIP or system PATH) | AST interpretation or transformation |
| Parser PHAR extraction with CRC32 integrity check | PHP installation on unsupported platforms |
| Process lifecycle, timeout, output caching | Downstream AST consumption |

**Bundled PHP platforms** (no system PHP needed): macOS x86_64/aarch64, Linux x86_64/aarch64, Windows x86_64.
Other platforms require PHP 7.1+ on system PATH.

## Installation

Add JitPack repository and dependency to `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.jhu-seclab-cobra:externs-phpparser:0.1.0")
}
```

## Usage

```kotlin
val parser = BinPhpParser().apply {
    dumpType = BinPhpParser.DumpType.JSON  // S_EXPR (default), VAR, JSON
    doPrettyPrint = true
    doResolveName = true
}

// parse a PHP file
parser.target = File("path/to/file.php")
val result = parser.execute()
// result.code: 0 = success, -1 = timeout
// result.output: File containing AST text

// or use executeWith for one-off config (restores original state after)
val result = parser.executeWith {
    target = File("path/to/another.php")
    doWithPositions = true
}
```

**Options**: `doPrettyPrint`, `doResolveName`, `doWithColInfo`, `doWithPositions`, `doWithRecovery` — all default to `false`.

**Caching**: Set `parser.doCacheOutput = true` to skip re-execution for identical commands. Cache key is the content hash of the command array.

**Timeout**: Default 1 minute. Set via `parser.timeout = Duration.ofMinutes(5)`.

## License

[GNU2.0](./LICENSE)
