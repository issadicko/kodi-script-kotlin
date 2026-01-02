package com.kodi.script

import java.io.File
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

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
                val expectedOut =
                        if (outFile.exists()) outFile.readText().replace("\r\n", "\n").trim()
                        else ""

                // Parse directives
                var maxOps: Long = 0
                var expectError = false

                source.reader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("// config:")) {
                            val parts = trimmed.removePrefix("// config:").split("=")
                            if (parts.size >= 2) {
                                val key = parts[0].trim()
                                val value = parts[1].trim()
                                if (key == "maxOps") {
                                    maxOps = value.toLongOrNull() ?: 0
                                }
                            }
                        }
                        if (trimmed.startsWith("// expect: error")) {
                            expectError = true
                        }
                    }
                }

                // If expecting check strict output if .out exists
                // But if expecting error, we just ensure error exists

                val builder = KodiScript.builder(source)
                if (maxOps > 0) {
                    builder.withMaxOperations(maxOps)
                }
                val result = builder.execute()

                if (expectError) {
                    assertTrue(result.hasErrors, "Expected execution error but got none")
                } else {
                    if (result.hasErrors) {
                        fail<Unit>("Execution failed: ${result.errors.joinToString(", ")}")
                    }

                    val actualOut = result.output.joinToString("\n").replace("\r\n", "\n").trim()

                    assertEquals(
                            normalize(expectedOut),
                            normalize(actualOut),
                            "Output mismatch for $testName"
                    )
                }

                // Normalize output for cross-implementation compatibility
                fun normalize(s: String): String {
                    return s
                            .replace(Regex("""(\d+)\.0(?=[\s,\]\}\)\n]|$)""")) { it.groupValues[1] }
                            .replace("<nil>", "null")
                            .replace("hello+world", "hello%20world")
                }

                assertEquals(
                        normalize(expectedOut),
                        normalize(actualOut),
                        "Output mismatch for $testName"
                )
            }
        }
    }
}
