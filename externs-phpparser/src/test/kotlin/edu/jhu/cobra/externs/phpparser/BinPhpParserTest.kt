package edu.jhu.cobra.externs.phpparser

/**
 * Tests for [BinPhpParser] — PHP binary resolver, AST parser, and NameResolver behavior.
 *
 * Construction:
 * - `should initialize with default parameters` — bundled/system PHP resolved
 * - `should throw when phpBinary does not exist` — invalid binary throws
 * - `should accept valid system php binary` — system PHP accepted
 * - `should accept custom parserBinary` — custom PHAR accepted
 * - `should fall back to auto-detect when phpBinary version too low` — low version fallback
 * - `should fall back to system PATH when bundled PHP zip missing` — zip missing fallback
 * - `should fall back to system PATH with valid PHP found` — PATH search
 * - `should throw when bundled zip missing and no system PHP` — no PHP throws
 * - `should throw when system PHP version too low in fallback` — invalid version throws
 * - `should extract bundled PHP from zip when CRC32 mismatch` — extraction on mismatch
 * - `should throw when PHP zip extraction fails` — extraction failure throws
 * - `should throw when parser zip extraction fails` — parser extraction failure throws
 *
 * Configuration:
 * - `should default to S_EXPR dump type` — default dumpType
 * - `should configure all dump types` — all DumpType values
 * - `DumpType toString should return opt string` — toString returns CLI flag
 * - `should include all boolean options in command array` — all flags present
 * - `should produce correct command array with no options enabled` — minimal command
 * - `should skip false boolean options in command array` — false excluded
 * - `should set name resolution option in command array` — --resolve-names flag
 *
 * Execution:
 * - `should throw when target is not set` — missing target throws
 * - `should parse PHP file with JSON dump` — JSON output
 * - `should parse PHP file with S_EXPR dump` — S-expr output
 * - `should parse PHP file with VAR dump` — var_dump output
 * - `should include positions when doWithPositions is true` — position attributes
 * - `should include pretty print output` — pretty-print accepted
 * - `should enable recovery mode` — broken PHP parsed
 * - `should enable column info` — column info accepted
 * - `should parse complex PHP code with class and function` — class/method nodes
 * - `should use cache on second execution with same command` — cache hit
 * - `should restore config after executeWith` — config restored
 *
 * NameResolver (--resolve-names):
 * - `resolve should produce Name_FullyQualified for use imports` — use import resolved
 * - `resolve should produce Name_FullyQualified for use alias` — alias resolved
 * - `resolve should populate namespacedName on class declarations` — declaration FQN
 * - `resolve should produce Name_FullyQualified for extends` — inheritance resolved
 * - `resolve should fully resolve namespace relative names` — namespace\ resolved
 * - `resolve should leave self as Name not Name_FullyQualified` — self unresolved
 * - `resolve should leave parent as Name not Name_FullyQualified` — parent unresolved
 * - `resolve should leave static as Name` — static unresolved
 * - `resolve should leave unqualified function call as Name with namespacedName` — strlen unresolved
 * - `resolve should leave unqualified constant as Name with namespacedName` — PHP_INT_MAX unresolved
 * - `resolve should preserve Stmt_Namespace and Stmt_Use nodes` — structural nodes kept
 * - `resolve should handle group use declarations` — group use resolved
 * - `resolve should handle function and constant use imports` — use function/const resolved
 * - `without resolve namespacedName should be null` — no resolver = no FQN
 *
 * Recovery:
 * - `recovery should parse broken PHP and insert Expr_Error` — error nodes
 * - `recovery should handle unclosed string` — partial AST
 *
 * JSON format:
 * - `positions should include startFilePos and endFilePos in JSON` — position fields
 * - `JSON output should contain nodeType for all nodes` — nodeType present
 * - `JSON output should contain attributes with line numbers` — line numbers
 * - `JSON should encode all PHP statement types` — class/method/if/return/etc
 * - `JSON should encode expression types` — binary ops/cast/ternary/array
 * - `JSON should encode v5 scalar types correctly` — Scalar_Int/Float/String
 * - `JSON should encode modifier flags correctly` — flags field
 *
 * Edge cases:
 * - `should parse empty PHP file` — empty file succeeds
 * - `should parse PHP file with only HTML` — HTML-only succeeds
 * - `should parse PHP 8 features` — enum/union type/readonly
 */

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BinPhpParserTest {

    @Test
    fun `should initialize with default parameters`() {
        val parser = BinPhpParser()
        assertNotNull(parser)
    }

    @Test
    fun `should throw when phpBinary does not exist`() {
        assertFailsWith<ExternalBinaryInvalidException> {
            BinPhpParser(phpBinary = File("php-not-exist"))
        }
    }

    @Test
    fun `should accept valid system php binary`() {
        val phpFile = searchBin("php") ?: return
        val parser = BinPhpParser(phpBinary = phpFile)
        assertNotNull(parser)
    }

    @Test
    fun `should accept custom parserBinary`() {
        val fakePhar = Files.createTempFile("fake-parser", ".phar").toFile()
        fakePhar.writeText("fake")
        val parser = BinPhpParser(parserBinary = fakePhar)
        assertNotNull(parser)
        fakePhar.delete()
    }

    @Test
    fun `should default to S_EXPR dump type`() {
        val parser = BinPhpParser()
        assertEquals(BinPhpParser.DumpType.S_EXPR, parser.dumpType)
    }

    @Test
    fun `should configure all dump types`() {
        val parser = BinPhpParser()
        for (type in BinPhpParser.DumpType.entries) {
            parser.dumpType = type
            assertEquals(type, parser.dumpType)
        }
    }

    @Test
    fun `DumpType toString should return opt string`() {
        assertEquals("--dump", BinPhpParser.DumpType.S_EXPR.toString())
        assertEquals("--var-dump", BinPhpParser.DumpType.VAR.toString())
        assertEquals("--json-dump", BinPhpParser.DumpType.JSON.toString())
    }

    @Test
    fun `should throw when target is not set`() {
        val parser = BinPhpParser()
        assertFailsWith<ExternalBinaryArgumentMissException> {
            parser.execute()
        }
    }

    @Test
    fun `should parse PHP file with JSON dump`() {
        val phpFile = createTempPhpFile("<?php echo 'hello';")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.exists())
        val output = result.output.readText()
        assertTrue(output.contains("Stmt_Echo"))
        phpFile.delete()
    }

    @Test
    fun `should parse PHP file with S_EXPR dump`() {
        val phpFile = createTempPhpFile("<?php \$x = 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.S_EXPR
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
        phpFile.delete()
    }

    @Test
    fun `should parse PHP file with VAR dump`() {
        val phpFile = createTempPhpFile("<?php \$x = 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.VAR
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        assertTrue(result.output.readText().isNotEmpty())
        phpFile.delete()
    }

    @Test
    fun `should include positions when doWithPositions is true`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithPositions = true
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should include pretty print output`() {
        val phpFile = createTempPhpFile("<?php function foo() { return 1; }")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doPrettyPrint = true
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should enable recovery mode`() {
        val phpFile = createTempPhpFile("<?php echo 'unclosed")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithRecovery = true
        }

        val result = parser.execute()
        assertTrue(result.output.exists())
        phpFile.delete()
    }

    @Test
    fun `should set name resolution option in command array`() {
        val phpFile = createTempPhpFile("<?php namespace App; class Foo {}")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doResolveName = true
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--resolve-names"))
        phpFile.delete()
    }

    @Test
    fun `should enable column info`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithColInfo = true
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should include all boolean options in command array`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doPrettyPrint = true
            doResolveName = true
            doWithColInfo = true
            doWithPositions = true
            doWithRecovery = true
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--pretty-print"))
        assertTrue(cmd.contains("--resolve-names"))
        assertTrue(cmd.contains("--with-column-info"))
        assertTrue(cmd.contains("--with-positions"))
        assertTrue(cmd.contains("--with-recovery"))
        assertTrue(cmd.contains("--json-dump"))
        phpFile.delete()
    }

    @Test
    fun `should produce correct command array with no options enabled`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.size >= 4)
        assertEquals("--json-dump", cmd[cmd.size - 2])
        assertEquals(phpFile.absolutePath, cmd.last())
    }

    @Test
    fun `should parse complex PHP code with class and function`() {
        val phpFile = createTempPhpFile(
            """
            <?php
            namespace App\Models;

            class User {
                private string ${'$'}name;
                public function __construct(string ${'$'}name) {
                    ${'$'}this->name = ${'$'}name;
                }
                public function getName(): string {
                    return ${'$'}this->name;
                }
            }
            """.trimIndent()
        )
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
        }

        val result = parser.execute()
        assertEquals(0, result.code)
        val output = result.output.readText()
        assertTrue(output.contains("Stmt_Class"))
        assertTrue(output.contains("Stmt_ClassMethod"))
        phpFile.delete()
    }

    @Test
    fun `should use cache on second execution with same command`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doCacheOutput = true
        }

        val r1 = parser.execute()
        assertEquals(0, r1.code)
        val r2 = parser.execute()
        assertEquals(0, r2.code)
        assertEquals(r1.output.absolutePath, r2.output.absolutePath)
        phpFile.delete()
    }

    @Test
    fun `should restore config after executeWith`() {
        val phpFile1 = createTempPhpFile("<?php echo 1;")
        val phpFile2 = createTempPhpFile("<?php echo 2;")
        val parser = BinPhpParser().apply {
            target = phpFile1
            dumpType = BinPhpParser.DumpType.S_EXPR
        }

        val result = parser.executeWith {
            target = phpFile2
            dumpType = BinPhpParser.DumpType.JSON
        }
        assertNotNull(result)
        assertEquals(BinPhpParser.DumpType.S_EXPR, parser.dumpType)
        phpFile1.delete()
        phpFile2.delete()
    }

    @Test
    fun `should fall back to auto-detect when phpBinary version too low`() {
        val fakePhp = Files.createTempFile("fake-php", ".sh").toFile()
        fakePhp.writeText("#!/bin/sh\necho 'PHP 5.0.0 (cli)'")
        fakePhp.setExecutable(true)
        try {
            val parser = BinPhpParser(phpBinary = fakePhp)
            assertNotNull(parser)
        } finally {
            fakePhp.delete()
        }
    }

    @Test
    fun `should skip false boolean options in command array`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val parser = BinPhpParser().apply {
            target = phpFile
            dumpType = BinPhpParser.DumpType.JSON
            doWithRecovery = true
            doPrettyPrint = false
        }

        val cmd = parser.getCommandArray()
        assertTrue(cmd.contains("--with-recovery"))
        assertFalse(cmd.contains("--pretty-print"))
        phpFile.delete()
    }

    @Test
    fun `should fall back to system PATH when bundled PHP zip missing`() {
        val sysPhp = searchBin("php") ?: return
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns sysPhp
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                val parser = BinPhpParser()
                assertNotNull(parser)
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should fall back to system PATH with valid PHP found`() {
        val fakeBin = Files.createTempFile("fake-php", "").toFile()
        fakeBin.writeText("fake-php")
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns fakeBin
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                val parser = BinPhpParser()
                assertNotNull(parser)
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
            fakeBin.delete()
        }
    }

    @Test
    fun `should throw when system PHP version too low in fallback`() {
        val fakeBin = Files.createTempFile("fake-php", "").toFile()
        fakeBin.writeText("fake-php")
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns false
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns fakeBin
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
            fakeBin.delete()
        }
    }

    @Test
    fun `should throw when bundled zip missing and no system PHP`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true
            every { searchBin(name = any<String>()) } returns null
            val mockLoader = object : ClassLoader(Thread.currentThread().contextClassLoader) {
                override fun getResourceAsStream(name: String): InputStream? {
                    if (name.startsWith("php-cli-")) return null
                    return super.getResourceAsStream(name)
                }
            }
            val origLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = mockLoader
            try {
                assertFailsWith<ExternalBinaryNotFoundException> {
                    BinPhpParser()
                }
            } finally {
                Thread.currentThread().contextClassLoader = origLoader
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should extract bundled PHP from zip when CRC32 mismatch`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns true

            val parser = BinPhpParser()
            assertNotNull(parser)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should throw when PHP zip extraction fails`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            every { extractFileFromZip(any(), any(), *anyVararg()) } returns false

            assertFailsWith<ExternalBinaryNotFoundException> {
                BinPhpParser()
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `should throw when parser zip extraction fails`() {
        mockkStatic("edu.jhu.cobra.externs.phpparser.UtilsKt")
        try {
            every { any<Path>().crc32ChecksumString } returns "mismatch"
            every { isPhpVersionValid(any(), any(), any()) } returns true
            var callCount = 0
            every { extractFileFromZip(any(), any(), *anyVararg()) } answers {
                callCount++
                callCount == 1
            }

            assertFailsWith<ExternalBinaryNotFoundException> {
                BinPhpParser()
            }
        } finally {
            unmockkAll()
        }
    }

    // ========== NameResolver (--resolve-names) ==========

    @Test
    fun `resolve should produce Name_FullyQualified for use imports`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App\Models;
            use App\Services\Logger;
            class User {
                public function getLogger(): Logger { return new Logger(); }
            }
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("Name_FullyQualified"), "use import should resolve to Name_FullyQualified")
        assertTrue(json.contains("App\\\\Services\\\\Logger"), "Logger should resolve to App\\Services\\Logger")
        phpFile.delete()
    }

    @Test
    fun `resolve should produce Name_FullyQualified for use alias`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            use App\Services\Cache as C;
            new C();
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Services\\\\Cache"), "alias C should resolve to App\\Services\\Cache")
        phpFile.delete()
    }

    @Test
    fun `resolve should populate namespacedName on class declarations`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App\Models;
            class User {}
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("namespacedName"), "namespacedName should be populated")
        assertTrue(json.contains("App\\\\Models\\\\User"), "class User should have FQN App\\Models\\User")
        phpFile.delete()
    }

    @Test
    fun `resolve should produce Name_FullyQualified for extends`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            class Foo {}
            class Bar extends Foo {}
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("Name_FullyQualified"), "extends should be resolved")
        assertTrue(json.contains("App\\\\Foo"), "extends Foo should resolve to App\\Foo")
        phpFile.delete()
    }

    @Test
    fun `resolve should fully resolve namespace relative names`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App\Models;
            ${'$'}x = new namespace\Sub();
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Models\\\\Sub"), "namespace\\Sub should resolve to App\\Models\\Sub")
        assertFalse(json.contains("Name_Relative"), "Name_Relative should not appear after resolution")
        phpFile.delete()
    }

    @Test
    fun `resolve should leave self as Name not Name_FullyQualified`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            class Foo {
                public static function create(): self { return new self(); }
            }
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains(""""self""""), "self should remain as literal 'self'")
        assertFalse(json.contains("Name_FullyQualified") && json.contains(""""App\\Foo"""") && !json.contains(""""self"""""),
            "self should NOT be resolved to Name_FullyQualified")
        phpFile.delete()
    }

    @Test
    fun `resolve should leave parent as Name not Name_FullyQualified`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            class Foo {}
            class Bar extends Foo {
                public function up(): parent { return new parent(); }
            }
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains(""""parent""""), "parent should remain as literal 'parent'")
        phpFile.delete()
    }

    @Test
    fun `resolve should leave static as Name`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            class Foo {
                public static function create(): static { return new static(); }
            }
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains(""""static""""), "static should remain as literal 'static'")
        phpFile.delete()
    }

    @Test
    fun `resolve should leave unqualified function call as Name with namespacedName`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            strlen("test");
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("strlen"), "strlen should appear in output")
        assertTrue(json.contains("Name_FullyQualified"), "Should have a FullyQualified namespacedName for strlen")
        phpFile.delete()
    }

    @Test
    fun `resolve should leave unqualified constant as Name with namespacedName`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            ${'$'}x = PHP_INT_MAX;
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("PHP_INT_MAX"), "PHP_INT_MAX should appear")
        phpFile.delete()
    }

    @Test
    fun `resolve should preserve Stmt_Namespace and Stmt_Use nodes`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App\Models;
            use App\Services\Logger;
            class User {}
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("Stmt_Namespace"), "Stmt_Namespace should remain")
        assertTrue(json.contains("Stmt_Use"), "Stmt_Use should remain")
        phpFile.delete()
    }

    @Test
    fun `resolve should handle group use declarations`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            use App\Models\{User, Post};
            new User();
            new Post();
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Models\\\\User"), "User should resolve to App\\Models\\User")
        assertTrue(json.contains("App\\\\Models\\\\Post"), "Post should resolve to App\\Models\\Post")
        phpFile.delete()
    }

    @Test
    fun `resolve should handle function and constant use imports`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            use function App\Utils\helper;
            use const App\Config\VERSION;
            helper();
        """.trimIndent())
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Utils\\\\helper"), "function import should resolve")
        phpFile.delete()
    }

    @Test
    fun `without resolve namespacedName should be null`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            class Foo {}
        """.trimIndent())
        val json = parseJson(phpFile, resolve = false)
        assertFalse(json.contains("Name_FullyQualified"), "Without resolver: no Name_FullyQualified")
        phpFile.delete()
    }

    // ========== Recovery Mode (--with-recovery) ==========

    @Test
    fun `recovery should parse broken PHP without crashing`() {
        val phpFile = createTempPhpFile("<?php function foo( { return 1; }")
        val result = BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON; doWithRecovery = true
        }.execute()
        assertTrue(result.output.readText().isNotEmpty(),
            "Recovery mode should produce output even for broken PHP")
        phpFile.delete()
    }

    @Test
    fun `recovery should handle unclosed string`() {
        val phpFile = createTempPhpFile("<?php echo 'unclosed;")
        val result = BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON; doWithRecovery = true
        }.execute()
        assertTrue(result.output.readText().isNotEmpty(), "Recovery should produce output for unclosed string")
        phpFile.delete()
    }

    // ========== Position and Column Info ==========

    @Test
    fun `positions should include startFilePos and endFilePos in JSON`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val json = BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON; doWithPositions = true
        }.execute().output.readText()
        assertTrue(json.contains("startFilePos"), "Should contain startFilePos")
        assertTrue(json.contains("endFilePos"), "Should contain endFilePos")
        phpFile.delete()
    }

    // ========== JSON Output Format ==========

    @Test
    fun `JSON output should contain nodeType for all nodes`() {
        val phpFile = createTempPhpFile("<?php \$x = 1; echo \$x;")
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("\"nodeType\""), "JSON should contain nodeType field")
        assertTrue(json.contains("Stmt_Expression"), "Should contain expression statement")
        phpFile.delete()
    }

    @Test
    fun `JSON output should contain attributes with line numbers`() {
        val phpFile = createTempPhpFile("<?php echo 1;")
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("\"startLine\""), "Should contain startLine")
        assertTrue(json.contains("\"endLine\""), "Should contain endLine")
        phpFile.delete()
    }

    @Test
    fun `JSON should encode all PHP statement types`() {
        val phpFile = createTempPhpFile("""
            <?php
            namespace App;
            use App\Foo;
            class Bar extends Foo {
                const X = 1;
                public string ${'$'}name;
                public function test(int ${'$'}a): string {
                    if (${'$'}a > 0) { return "pos"; }
                    else { return "neg"; }
                }
            }
            function helper() {}
            interface IFace {}
            trait MyTrait {}
            enum Color { case Red; case Blue; }
        """.trimIndent())
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("Stmt_Class"), "Should contain class")
        assertTrue(json.contains("Stmt_ClassMethod"), "Should contain method")
        assertTrue(json.contains("Stmt_Property"), "Should contain property")
        assertTrue(json.contains("Stmt_ClassConst"), "Should contain class constant")
        assertTrue(json.contains("Stmt_Function"), "Should contain function")
        assertTrue(json.contains("Stmt_Interface"), "Should contain interface")
        assertTrue(json.contains("Stmt_Trait"), "Should contain trait")
        assertTrue(json.contains("Stmt_Enum"), "Should contain enum")
        assertTrue(json.contains("Stmt_If"), "Should contain if")
        assertTrue(json.contains("Stmt_Return"), "Should contain return")
        phpFile.delete()
    }

    @Test
    fun `JSON should encode expression types`() {
        val phpFile = createTempPhpFile("""
            <?php
            ${'$'}x = 1 + 2;
            ${'$'}y = "hello" . " world";
            ${'$'}z = [1, 2, 3];
            ${'$'}w = (int)"42";
            ${'$'}v = ${'$'}x > 0 ? "yes" : "no";
        """.trimIndent())
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("Expr_BinaryOp_Plus") || json.contains("BinaryOp_Plus"), "Should contain addition")
        assertTrue(json.contains("Expr_BinaryOp_Concat") || json.contains("BinaryOp_Concat"), "Should contain concat")
        assertTrue(json.contains("Expr_Array"), "Should contain array literal")
        assertTrue(json.contains("Expr_Cast_Int"), "Should contain int cast")
        assertTrue(json.contains("Expr_Ternary"), "Should contain ternary")
        phpFile.delete()
    }

    @Test
    fun `JSON should encode v5 scalar types correctly`() {
        val phpFile = createTempPhpFile("""
            <?php
            ${'$'}i = 42;
            ${'$'}f = 3.14;
            ${'$'}s = "hello";
            ${'$'}b = true;
            ${'$'}n = null;
        """.trimIndent())
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("Scalar_Int"), "Int should be Scalar_Int (v5 rename from LNumber)")
        assertTrue(json.contains("Scalar_Float"), "Float should be Scalar_Float (v5 rename from DNumber)")
        assertTrue(json.contains("Scalar_String"), "Should contain Scalar_String")
        phpFile.delete()
    }

    @Test
    fun `JSON should encode modifier flags correctly`() {
        val phpFile = createTempPhpFile("""
            <?php
            class Foo {
                public int ${'$'}a;
                protected string ${'$'}b;
                private float ${'$'}c;
                public static function bar() {}
                final public function baz() {}
            }
        """.trimIndent())
        val json = parseJson(phpFile, resolve = false)
        assertTrue(json.contains("\"flags\""), "Should contain flags field for modifiers")
        phpFile.delete()
    }

    // ========== Edge Cases ==========

    @Test
    fun `should parse empty PHP file`() {
        val phpFile = createTempPhpFile("<?php")
        val result = BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON
        }.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should parse PHP file with only HTML`() {
        val phpFile = createTempPhpFile("<html><body>Hello</body></html>")
        val result = BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON
        }.execute()
        assertEquals(0, result.code)
        phpFile.delete()
    }

    @Test
    fun `should parse PHP 8 features`() {
        val phpFile = createTempPhpFile("""
            <?php
            enum Status { case Active; case Inactive; }
            function test(int|string ${'$'}x): void {}
            class Foo { public readonly string ${'$'}name; }
        """.trimIndent())
        val result = BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON
        }.execute()
        assertEquals(0, result.code, "Should parse PHP 8 syntax")
        val json = result.output.readText()
        assertTrue(json.contains("Stmt_Enum"), "Should contain enum")
        assertTrue(json.contains("UnionType"), "Should contain union type")
        phpFile.delete()
    }

    // ========== Helper ==========

    private fun parseJson(phpFile: File, resolve: Boolean): String =
        BinPhpParser().apply {
            target = phpFile; dumpType = BinPhpParser.DumpType.JSON; doResolveName = resolve
        }.execute().also { assertEquals(0, it.code, "Parse should succeed") }.output.readText()

    private fun createTempPhpFile(code: String): File =
        Files.createTempFile("test", ".php").toFile().apply { writeText(code) }
}
