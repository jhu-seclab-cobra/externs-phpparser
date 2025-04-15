# PHP-Parser.kt

A Kotlin library that provides a wrapper for PHP-Parser 4.19.4, enabling PHP code parsing and AST generation in Kotlin applications. The library includes a self-contained PHP environment for specific platforms, making it system-independent for those platforms.

## Features

- Self-contained PHP environment for supported platforms
- Cross-platform compatibility
- Built-in binary version validation and checksum verification
- Configurable parsing options (pretty printing, name resolution, position info, etc.)

## Requirements

- Java 8 or higher
- PHP 7.1+ (only required for systems not listed below)

### Self-contained Environment Support

The library includes a complete PHP environment for the following platforms:
- macOS (Intel x86_64 and Apple Silicon aarch64)
- Linux (x86_64 and aarch64)
- Windows (x86_64)

For other platforms, the library will use the PHP executable from your system (PHP 7.1+ required).

## Installation

1. Add JitPack repository to your `build.gradle.kts`:
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

2. Add the dependency:
```kotlin
dependencies {
    // Use the latest release version or a specific commit hash
    implementation("com.github.COBRA-Static-Analysis:phpparser.kt:1.0.0")
}
```

## Usage

### Kotlin

```kotlin
import cobra.extern.phpparser.BinPhpParser
import java.io.File

// Create a parser instance with configuration
val custPhpParser = BinPhpParser().apply {
    dumpType = BinPhpParser.DumpType.JSON
    doPrettyPrint = true
    doResolveName = true
    doWithPositions = true
    doWithColInfo = true
    doWithRecovery = true
}

// Parse a PHP file with temporary configuration
val result = custPhpParser.executeWith {
    target = File("path/to/your/php_file_or_project")
}

// Get the output
println(result.output)
```

### Java

```java
import cobra.extern.phpparser.BinPhpParser;
import java.io.File;

// Create a parser instance
BinPhpParser parser = new BinPhpParser();

// Configure parsing options
parser.setTarget(new File("path/to/your/php_file_or_project"));
parser.setDumpType(BinPhpParser.DumpType.JSON);
parser.setDoPrettyPrint(true);
parser.setDoResolveName(true);
parser.setDoWithPositions(true);
parser.setDoWithColInfo(true);
parser.setDoWithRecovery(true);

// Parse the PHP file
BinaryResult result = parser.execute();

// Get the output
System.out.println(result.getOutput());
```

### Output Formats

The parser supports three output formats:
- `DumpType.S_EXPR`: S-Expression format (default)
- `DumpType.VAR`: Var Dump format
- `DumpType.JSON`: JSON format

### Environment Management

The library:
1. For supported platforms (macOS, Windows, Linux):
   - Uses the bundled PHP environment
   - No system PHP installation required
   - Completely system-independent
2. For other platforms:
   - Uses the system's PHP executable (PHP 7.1+ required)
   - Requires PHP to be installed and available in the system PATH

## License

[GNU2.0](./LICENSE)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
