package com.kodi.script

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KodiScriptTest {

    @Test
    fun `test basic variable declaration`() {
        val result = KodiScript.run("let x = 42")
        assertFalse(result.hasErrors)
        assertEquals(42.0, result.value)
    }

    @Test
    fun `test string concatenation`() {
        val result =
                KodiScript.run(
                        """
            let name = "Kodi"
            let greeting = "Hello " + name
            greeting
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals("Hello Kodi", result.value)
    }

    @Test
    fun `test null-safety elvis operator`() {
        val result =
                KodiScript.run(
                        """
            let x = null
            let y = x ?: "default"
            y
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals("default", result.value)
    }

    @Test
    fun `test host variables`() {
        val vars = mapOf("user" to mapOf("name" to "Alice", "age" to 30.0))
        val result = KodiScript.run("user.name", vars)
        assertFalse(result.hasErrors)
        assertEquals("Alice", result.value)
    }

    @Test
    fun `test safe access on null`() {
        val vars = mapOf("user" to null)
        val result =
                KodiScript.run(
                        """
            let status = user?.name ?: "unknown"
            status
        """.trimIndent(),
                        vars
                )
        assertFalse(result.hasErrors)
        assertEquals("unknown", result.value)
    }

    @Test
    fun `test if statement`() {
        val result =
                KodiScript.run(
                        """
            let x = 10
            let result = "none"
            if (x > 5) {
                result = "big"
            } else {
                result = "small"
            }
            result
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals("big", result.value)
    }

    @Test
    fun `test print output`() {
        val result =
                KodiScript.run(
                        """
            print("Hello")
            print("World")
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals(2, result.output.size)
        assertEquals("Hello", result.output[0])
        assertEquals("World", result.output[1])
    }

    @Test
    fun `test arithmetic operations`() {
        val tests =
                listOf(
                        "5 + 3" to 8.0,
                        "10 - 4" to 6.0,
                        "3 * 4" to 12.0,
                        "20 / 5" to 4.0,
                        "(2 + 3) * 4" to 20.0
                )

        tests.forEach { (source, expected) ->
            val result = KodiScript.run(source)
            assertFalse(result.hasErrors, "Errors in '$source': ${result.errors}")
            assertEquals(expected, result.value, "Failed for: $source")
        }
    }

    @Test
    fun `test boolean logic`() {
        val tests =
                listOf(
                        "true && true" to true,
                        "true && false" to false,
                        "false || true" to true,
                        "!false" to true,
                        "5 > 3" to true,
                        "5 == 5" to true,
                        "5 != 3" to true
                )

        tests.forEach { (source, expected) ->
            val result = KodiScript.run(source)
            assertFalse(result.hasErrors, "Errors in '$source': ${result.errors}")
            assertEquals(expected, result.value, "Failed for: $source")
        }
    }

    @Test
    fun `test native functions`() {
        // Base64
        val base64Result = KodiScript.run("""base64Encode("hello")""")
        assertFalse(base64Result.hasErrors)
        assertEquals("aGVsbG8=", base64Result.value)

        // JSON stringify
        val jsonResult = KodiScript.run("""jsonStringify("test")""")
        assertFalse(jsonResult.hasErrors)
        assertEquals("\"test\"", jsonResult.value)
    }

    @Test
    fun `test multiline expression`() {
        val result =
                KodiScript.run(
                        """
            let total = 10 +
            20 +
            30
            total
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals(60.0, result.value)
    }

    @Test
    fun `test optional semicolons`() {
        val result1 = KodiScript.run("let x = 1; let y = 2; x + y")
        val result2 =
                KodiScript.run(
                        """
            let x = 1
            let y = 2
            x + y
        """.trimIndent()
                )

        assertFalse(result1.hasErrors)
        assertFalse(result2.hasErrors)
        assertEquals(result1.value, result2.value)
    }

    @Test
    fun `test builder pattern with custom function`() {
        val result =
                KodiScript.builder(
                                """
            let greeting = customGreet("World")
            greeting
        """.trimIndent()
                        )
                        .registerFunction("customGreet") { args -> "Hello, ${args[0]}!" }
                        .execute()

        assertFalse(result.hasErrors)
        assertEquals("Hello, World!", result.value)
    }

    @Test
    fun `test eval throws on error`() {
        assertThrows(KodiScriptException::class.java) { KodiScript.eval("undefinedVariable") }
    }
}
