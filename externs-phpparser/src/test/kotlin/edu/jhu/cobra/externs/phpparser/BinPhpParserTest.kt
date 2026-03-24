package edu.jhu.cobra.externs.phpparser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import java.io.File
import java.nio.file.Files
import kotlin.test.assertTrue

class BinPhpParserTest {
    @Test
    fun testParserInitializationWithDefaultParameters() {
        val parser = BinPhpParser()
        assertNotNull(parser)
    }

    @Test
    fun testParserPhpExternalBinaryInvalidException() {
        val phpBinary = File("php-not-exist")
        assertFailsWith<ExternalBinaryInvalidException> {
            BinPhpParser(phpBinary = phpBinary)
        }
    }

    @Test
    fun testDumpTypeConfiguration() {
        val parser = BinPhpParser()
        assertEquals(BinPhpParser.DumpType.S_EXPR, parser.dumpType)
        
        parser.dumpType = BinPhpParser.DumpType.JSON
        assertEquals(BinPhpParser.DumpType.JSON, parser.dumpType)
        
        parser.dumpType = BinPhpParser.DumpType.VAR
        assertEquals(BinPhpParser.DumpType.VAR, parser.dumpType)
    }

    @Test
    fun testExecutePhpFileParsing() {
        // Create a temporary PHP file with some test code
        val tempPhpFile = Files.createTempFile("test", ".php")
        val phpCode = """
            <?php
            function testFunction(${'$'}param) {
                return ${'$'}param * 2;
            }
            
            class TestClass {
                private ${'$'}property;
                
                public function __construct(${'$'}value) {
                    ${'$'}this->property = ${'$'}value;
                }
                
                public function getProperty() {
                    return ${'$'}this->property;
                }
            }
        """.trimIndent()
        tempPhpFile.toFile().writeText(phpCode)

        // Initialize the parser and set the target file
        val parser = BinPhpParser().apply {
            target = tempPhpFile.toFile()
            dumpType = BinPhpParser.DumpType.JSON  // Use JSON format for easier verification
            doPrettyPrint = true
        }
        
        // Parse the PHP file
        val result = parser.execute()
        
        // Verify the result
        assertNotNull(result)
        assertEquals(0, result.code) // Success exit code
        assertNotNull(result.output)
        assertTrue(result.output.exists())
        
        // Clean up
        tempPhpFile.toFile().delete()
        result.output.delete()
    }
}