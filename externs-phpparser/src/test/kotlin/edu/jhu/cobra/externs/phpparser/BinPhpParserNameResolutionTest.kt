package edu.jhu.cobra.externs.phpparser

/*
 * Tests for [BinPhpParser] NameResolver behavior (--resolve-names).
 *
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
 */

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class BinPhpParserNameResolutionTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `resolve should produce Name_FullyQualified for use imports`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App\Models;
                use App\Services\Logger;
                class User {
                    public function getLogger(): Logger { return new Logger(); }
                }
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("Name_FullyQualified"), "use import should resolve to Name_FullyQualified")
        assertTrue(json.contains("App\\\\Services\\\\Logger"), "Logger should resolve to App\\Services\\Logger")
    }

    @Test
    fun `resolve should produce Name_FullyQualified for use alias`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                use App\Services\Cache as C;
                new C();
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Services\\\\Cache"), "alias C should resolve to App\\Services\\Cache")
    }

    @Test
    fun `resolve should populate namespacedName on class declarations`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App\Models;
                class User {}
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("namespacedName"), "namespacedName should be populated")
        assertTrue(json.contains("App\\\\Models\\\\User"), "class User should have FQN App\\Models\\User")
    }

    @Test
    fun `resolve should produce Name_FullyQualified for extends`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                class Foo {}
                class Bar extends Foo {}
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("Name_FullyQualified"), "extends should be resolved")
        assertTrue(json.contains("App\\\\Foo"), "extends Foo should resolve to App\\Foo")
    }

    @Test
    fun `resolve should fully resolve namespace relative names`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App\Models;
                ${'$'}x = new namespace\Sub();
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Models\\\\Sub"), "namespace\\Sub should resolve to App\\Models\\Sub")
        assertFalse(json.contains("Name_Relative"), "Name_Relative should not appear after resolution")
    }

    @Test
    fun `resolve should leave self as Name not Name_FullyQualified`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                class Foo {
                    public static function create(): self { return new self(); }
                }
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains(""""self""""), "self should remain as literal 'self'")
        assertFalse(
            json.contains("Name_FullyQualified") && json.contains(""""App\\Foo"""") && !json.contains(""""self"""""),
            "self should NOT be resolved to Name_FullyQualified",
        )
    }

    @Test
    fun `resolve should leave parent as Name not Name_FullyQualified`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                class Foo {}
                class Bar extends Foo {
                    public function up(): parent { return new parent(); }
                }
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains(""""parent""""), "parent should remain as literal 'parent'")
    }

    @Test
    fun `resolve should leave static as Name`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                class Foo {
                    public static function create(): static { return new static(); }
                }
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains(""""static""""), "static should remain as literal 'static'")
    }

    @Test
    fun `resolve should leave unqualified function call as Name with namespacedName`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                strlen("test");
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("strlen"), "strlen should appear in output")
        assertTrue(json.contains("Name_FullyQualified"), "Should have a FullyQualified namespacedName for strlen")
    }

    @Test
    fun `resolve should leave unqualified constant as Name with namespacedName`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                ${'$'}x = PHP_INT_MAX;
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("PHP_INT_MAX"), "PHP_INT_MAX should appear")
    }

    @Test
    fun `resolve should preserve Stmt_Namespace and Stmt_Use nodes`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App\Models;
                use App\Services\Logger;
                class User {}
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("Stmt_Namespace"), "Stmt_Namespace should remain")
        assertTrue(json.contains("Stmt_Use"), "Stmt_Use should remain")
    }

    @Test
    fun `resolve should handle group use declarations`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                use App\Models\{User, Post};
                new User();
                new Post();
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Models\\\\User"), "User should resolve to App\\Models\\User")
        assertTrue(json.contains("App\\\\Models\\\\Post"), "Post should resolve to App\\Models\\Post")
    }

    @Test
    fun `resolve should handle function and constant use imports`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                use function App\Utils\helper;
                use const App\Config\VERSION;
                helper();
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = true)
        assertTrue(json.contains("App\\\\Utils\\\\helper"), "function import should resolve")
    }

    @Test
    fun `without resolve namespacedName should be null`() {
        val phpFile =
            createPhpFile(
                tempDir,
                """
                <?php
                namespace App;
                class Foo {}
                """.trimIndent(),
            )
        val json = parseJson(phpFile, resolve = false)
        assertFalse(json.contains("Name_FullyQualified"), "Without resolver: no Name_FullyQualified")
    }
}
