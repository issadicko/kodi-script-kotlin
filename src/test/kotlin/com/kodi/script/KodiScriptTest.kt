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

    @Test
    fun `test return statement`() {
        val result = KodiScript.run("return 42")
        assertFalse(result.hasErrors)
        assertEquals(42.0, result.value)
    }

    @Test
    fun `test return with expression`() {
        val result = KodiScript.run("return 10 + 20")
        assertFalse(result.hasErrors)
        assertEquals(30.0, result.value)
    }

    @Test
    fun `test return stops execution`() {
        val result =
                KodiScript.run(
                        """
            let x = 10
            return x * 2
            let y = 100
            y
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals(20.0, result.value)
    }

    @Test
    fun `test return in if block`() {
        val result =
                KodiScript.run(
                        """
            let x = 5
            if (x > 3) {
                return "big"
            }
            return "small"
        """.trimIndent()
                )
        assertFalse(result.hasErrors)
        assertEquals("big", result.value)
    }

    @Test
    fun `test math functions`() {
        assertEquals(5.0, KodiScript.run("abs(-5)").value)
        assertEquals(3.0, KodiScript.run("floor(3.7)").value)
        assertEquals(4.0, KodiScript.run("ceil(3.2)").value)
        assertEquals(4.0, KodiScript.run("round(3.5)").value)
        assertEquals(8.0, KodiScript.run("pow(2, 3)").value)
        assertEquals(4.0, KodiScript.run("sqrt(16)").value)
    }

    @Test
    fun `test min max functions`() {
        assertEquals(1.0, KodiScript.run("min(5, 3, 1, 8)").value)
        assertEquals(8.0, KodiScript.run("max(5, 3, 1, 8)").value)
    }

    @Test
    fun `test string functions`() {
        assertEquals("HELLO", KodiScript.run("""toUpperCase("hello")""").value)
        assertEquals("hello", KodiScript.run("""toLowerCase("HELLO")""").value)
        assertEquals("hello", KodiScript.run("""trim("  hello  ")""").value)
        assertEquals(
                "hello kodi",
                KodiScript.run("""replace("hello world", "world", "kodi")""").value
        )
        assertEquals(true, KodiScript.run("""contains("hello world", "world")""").value)
        assertEquals(true, KodiScript.run("""startsWith("hello world", "hello")""").value)
        assertEquals(true, KodiScript.run("""endsWith("hello world", "world")""").value)
        assertEquals(6.0, KodiScript.run("""indexOf("hello world", "world")""").value)
    }

    @Test
    fun `test crypto functions`() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", KodiScript.run("""md5("hello")""").value)
        assertEquals(
                "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d",
                KodiScript.run("""sha1("hello")""").value
        )
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                KodiScript.run("""sha256("hello")""").value
        )
    }

    @Test
    fun `test random functions`() {
        val randomResult = KodiScript.run("random()")
        assertFalse(randomResult.hasErrors)
        val value = randomResult.value as Double
        assertTrue(value >= 0.0 && value < 1.0)

        val uuidResult = KodiScript.run("randomUUID()")
        assertFalse(uuidResult.hasErrors)
        assertEquals(36, (uuidResult.value as String).length)
    }

    @Test
    fun `test type check functions`() {
        assertEquals(true, KodiScript.run("isNumber(42)").value)
        assertEquals(false, KodiScript.run("""isNumber("42")""").value)
        assertEquals(true, KodiScript.run("""isString("hello")""").value)
        assertEquals(true, KodiScript.run("isBool(true)").value)
        assertEquals("number", KodiScript.run("typeOf(42)").value)
        assertEquals("string", KodiScript.run("""typeOf("hello")""").value)
        assertEquals("null", KodiScript.run("typeOf(null)").value)
    }

    @Test
    fun `test array functions`() {
        val vars =
                mapOf(
                        "numbers" to listOf(3.0, 1.0, 4.0, 1.0, 5.0),
                        "users" to
                                listOf(
                                        mapOf("name" to "Charlie", "age" to 30.0),
                                        mapOf("name" to "Alice", "age" to 25.0),
                                        mapOf("name" to "Bob", "age" to 35.0)
                                )
                )

        // sort
        val sortResult = KodiScript.run("sort(numbers)", vars)
        assertFalse(sortResult.hasErrors)
        @Suppress("UNCHECKED_CAST") val sorted = sortResult.value as List<Double>
        assertEquals(1.0, sorted.first())
        assertEquals(5.0, sorted.last())

        // size
        assertEquals(5.0, KodiScript.run("size(numbers)", vars).value)

        // first/last
        assertEquals(3.0, KodiScript.run("first(numbers)", vars).value)
        assertEquals(5.0, KodiScript.run("last(numbers)", vars).value)
    }

    @Test
    fun `test for loop`() {
        val vars = mapOf("numbers" to listOf(1.0, 2.0, 3.0, 4.0, 5.0))

        // Basic for loop with sum
        val sumResult =
                KodiScript.run(
                        """
let sum = 0
for (n in numbers) {
    sum = sum + n
}
sum
""",
                        vars
                )
        assertFalse(sumResult.hasErrors)
        assertEquals(15.0, sumResult.value)

        // For loop with print
        val printResult = KodiScript.run("""
for (item in numbers) {
    print(item)
}
""", vars)
        assertFalse(printResult.hasErrors)
        assertEquals(5, printResult.output.size)

        // For loop with objects
        val users = mapOf("users" to listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob")))
        val objResult = KodiScript.run("""
for (user in users) {
    print(user.name)
}
""", users)
        assertFalse(objResult.hasErrors)
        assertEquals(2, objResult.output.size)
        assertEquals("Alice", objResult.output[0])

        // Test if inside for loop - count specific values
        val ifResult =
                KodiScript.run(
                        """
let count = 0
for (n in numbers) {
    if (n == 2) {
        count = count + 1
    }
    if (n == 4) {
        count = count + 1
    }
}
count
""",
                        vars
                )
        assertFalse(ifResult.hasErrors, "Errors: ${ifResult.errors}")
        assertEquals(2.0, ifResult.value)

        // Test conditional print inside for loop
        val condResult =
                KodiScript.run(
                        """
for (n in numbers) {
    if (n > 3) {
        print(n)
    }
}
""",
                        vars
                )
        assertFalse(condResult.hasErrors)
        assertEquals(2, condResult.output.size)

        // Test if-else inside for loop
        val ifElseResult =
                KodiScript.run(
                        """
let big = 0
let small = 0
for (n in numbers) {
    if (n > 3) {
        big = big + 1
    } else {
        small = small + 1
    }
}
big
""",
                        vars
                )
        assertFalse(ifElseResult.hasErrors)
        assertEquals(2.0, ifElseResult.value)
    }

    @Test
    fun `test array and object literals`() {
        // Test array literal
        val arrayResult = KodiScript.run("let arr = [1, 2, 3]; arr", emptyMap())
        assertFalse(arrayResult.hasErrors)
        val arr = arrayResult.value as List<*>
        assertEquals(3, arr.size)
        assertEquals(1.0, arr[0])

        // Test array index
        val arrayIndexResult = KodiScript.run("let arr = [10, 20, 30]; arr[1]", emptyMap())
        assertFalse(arrayIndexResult.hasErrors)
        assertEquals(20.0, arrayIndexResult.value)

        // Test object literal
        val objResult =
                KodiScript.run("let obj = {name: \"Kodi\", age: 10}; obj[\"name\"]", emptyMap())
        assertFalse(objResult.hasErrors)
        assertEquals("Kodi", objResult.value)

        // Test object literal with identifier keys
        val objIdentResult =
                KodiScript.run("let obj = {name: \"Kodi\", age: 10}; obj[\"age\"]", emptyMap())
        assertFalse(objIdentResult.hasErrors)
        assertEquals(10.0, objIdentResult.value)

        // Test nested
        val nestedResult =
                KodiScript.run(
                        "let data = {users: [{name: \"Alice\"}]}; data[\"users\"][0][\"name\"]",
                        emptyMap()
                )
        assertFalse(nestedResult.hasErrors)
        assertEquals("Alice", nestedResult.value)
    }

    @Test
    fun `test user functions`() {
        // 1. Basic function call with implicit return
        val basicResult =
                KodiScript.run(
                        """
            let add = fn(x, y) { x + y };
            add(5, 5)
        """,
                        emptyMap()
                )
        assertFalse(basicResult.hasErrors)
        assertEquals(10.0, basicResult.value)

        // 2. Explicit return
        val explicitResult =
                KodiScript.run(
                        """
            let add = fn(x, y) { return x + y; };
            add(10, 10)
        """,
                        emptyMap()
                )
        assertFalse(explicitResult.hasErrors)
        assertEquals(20.0, explicitResult.value)

        // 3. Closure
        val closureResult =
                KodiScript.run(
                        """
            let newAdder = fn(x) {
                fn(y) { x + y }
            };
            let addTwo = newAdder(2);
            addTwo(2)
        """,
                        emptyMap()
                )
        assertFalse(closureResult.hasErrors)
        assertEquals(4.0, closureResult.value)

        // 4. Higher order
        val higherOrderResult =
                KodiScript.run(
                        """
            let apply = fn(f, x) { f(x) };
            let double = fn(x) { x * 2 };
            apply(double, 5)
        """,
                        emptyMap()
                )
        assertFalse(higherOrderResult.hasErrors)
        assertEquals(10.0, higherOrderResult.value)

        // 5. Recursion
        val recursionResult =
                KodiScript.run(
                        """
            let fact = fn(n) {
                if (n == 0) { return 1 }
                return n * fact(n - 1)
            };
            fact(5)
        """,
                        emptyMap()
                )
        assertFalse(recursionResult.hasErrors)
        assertEquals(120.0, recursionResult.value)
    }
}
