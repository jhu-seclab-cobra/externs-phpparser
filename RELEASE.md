## Version 0.1.0 (Initial Release)

[![codecov](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser/branch/main/graph/badge.svg)](https://codecov.io/gh/jhu-seclab-cobra/externs-phpparser)
![Kotlin JVM](https://img.shields.io/badge/Kotlin%20JVM-2.0.1%20%7C%20JVM%201.8%2B-blue?logo=kotlin)
[![Release](https://img.shields.io/badge/release-v0.1.0-blue.svg)](https://github.com/jhu-seclab-cobra/externs-phpparser/releases/tag/v0.1.0)
[![last commit](https://img.shields.io/github/last-commit/jhu-seclab-cobra/externs-phpparser)](https://github.com/jhu-seclab-cobra/externs-phpparser/commits/main)
[![](https://jitpack.io/v/jhu-seclab-cobra/externs-phpparser.svg)](https://jitpack.io/#jhu-seclab-cobra/externs-phpparser)
![Repo Size](https://img.shields.io/github/repo-size/jhu-seclab-cobra/externs-phpparser)
[![license](https://img.shields.io/github/license/jhu-seclab-cobra/externs-phpparser)](./LICENSE)

### Features
- PHP Parser Integration
  - Wrapper for PHP-Parser 4.19.4
  - AST generation support
  - Multiple output formats (S-Expression, JSON, Var Dump)

- Self-contained PHP Environment
  - Bundled PHP 8.4 for supported platforms
  - Cross-platform compatibility
  - Built-in binary version validation
  - Checksum verification for security

### System Requirements
- Java 8 or higher
- For unsupported platforms: PHP 7.1+ installed in system PATH

### Installation
Add to your `build.gradle.kts`:
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.jhu-seclab-cobra:externs-phpparser:0.1.0")
}
```

### Configuration Options
- `dumpType`: Output format selection (S_EXPR, JSON, VAR)
- `doPrettyPrint`: Enable formatted output
- `doResolveName`: Enable name resolution in AST
- `doWithColInfo`: Include column information
- `doWithPositions`: Include position information
- `doWithRecovery`: Enable error recovery

### Known Issues
- For platforms without bundled PHP environment, system PHP 7.1+ must be available in PATH
- Windows support limited to x86_64 architecture

### License
[GNU2.0](./LICENSE) 