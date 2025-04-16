# Release Notes

## Version 0.1.0 (Initial Release)

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