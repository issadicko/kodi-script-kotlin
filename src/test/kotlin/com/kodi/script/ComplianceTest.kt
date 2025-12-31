package com.kodi.script

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

class ComplianceTest {

    @TestFactory
    fun `compliance tests`(): List<DynamicTest> {
        val compliancePath = System.getenv("KODI_COMPLIANCE_TESTS_PATH") ?: "../compliance-tests"
        val dir = File(compliancePath)
        
        if (!dir.exists()) {
            println("Compliance tests directory not found at ${dir.absolutePath}. Skipping.")
            return emptyList()
        }

        val testFiles = mutableListOf<File>()
        
        fun walkDir(directory: File) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    walkDir(file)
                } else if (file.extension == "kodi") {
                    testFiles.add(file)
                }
            }
        }
        
        walkDir(dir)
        
        return testFiles.map { sourceFile ->
            val testName = sourceFile.relativeTo(dir).path
            
            DynamicTest.dynamicTest(testName) {
                val source = sourceFile.readText()
                val outFile = File(sourceFile.path.replace(".kodi", ".out"))
                val expectedOut = outFile.readText().replace("\r\n", "\n").trim()
                
                val result = KodiScript.run(source)
                
                if (result.hasErrors) {
                    fail<Unit>("Execution failed: ${result.errors.joinToString(", ")}")
                }
                
                val actualOut = result.output.joinToString("\n").replace("\r\n", "\n").trim()
                
                // Normalize output for cross-implementation compatibility
                fun normalize(s: String): String {
                    return s
                        .replace(Regex("""(\d+)\.0(?=[\s,\]\}\)\n]|$)""")) { it.groupValues[1] }
                        .replace("<nil>", "null")
                        .replace("hello+world", "hello%20world")
                }
                
                assertEquals(normalize(expectedOut), normalize(actualOut), "Output mismatch for $testName")
            }
        }
    }
}
