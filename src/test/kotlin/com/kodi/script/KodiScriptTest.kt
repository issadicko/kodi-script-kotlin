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
        fun `test modulo operator`() {
                val tests =
                        listOf("10 % 3" to 1.0, "10 % 2" to 0.0, "5 % 2" to 1.0, "5.5 % 2" to 1.5)

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
                assertThrows(KodiScriptException::class.java) {
                        KodiScript.eval("undefinedVariable")
                }
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
                assertEquals(
                        "5d41402abc4b2a76b9719d911017c592",
                        KodiScript.run("""md5("hello")""").value
                )
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
                val printResult =
                        KodiScript.run("""
for (item in numbers) {
    print(item)
}
""", vars)
                assertFalse(printResult.hasErrors)
                assertEquals(5, printResult.output.size)

                // For loop with objects
                val users =
                        mapOf("users" to listOf(mapOf("name" to "Alice"), mapOf("name" to "Bob")))
                val objResult =
                        KodiScript.run("""
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
                        KodiScript.run(
                                "let obj = {name: \"Kodi\", age: 10}; obj[\"name\"]",
                                emptyMap()
                        )
                assertFalse(objResult.hasErrors)
                assertEquals("Kodi", objResult.value)

                // Test object literal with identifier keys
                val objIdentResult =
                        KodiScript.run(
                                "let obj = {name: \"Kodi\", age: 10}; obj[\"age\"]",
                                emptyMap()
                        )
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

        @Test
        fun `test string templates`() {
                // Basic variable interpolation
                val basicResult =
                        KodiScript.run(
                                """
            let name = "World"
            "Hello ${"$"}{name}!"
        """.trimIndent()
                        )
                assertFalse(basicResult.hasErrors, "Basic errors: ${basicResult.errors}")
                assertEquals("Hello World!", basicResult.value)

                // Expression interpolation
                val exprResult = KodiScript.run(""""Result: ${"$"}{2 + 3}"""")
                assertFalse(exprResult.hasErrors, "Expr errors: ${exprResult.errors}")
                assertEquals("Result: 5.0", exprResult.value)

                // Multiple interpolations
                val multiResult =
                        KodiScript.run(
                                """
            let a = "Hello"
            let b = "World"
            "${"$"}{a}, ${"$"}{b}!"
        """.trimIndent()
                        )
                assertFalse(multiResult.hasErrors, "Multi errors: ${multiResult.errors}")
                assertEquals("Hello, World!", multiResult.value)

                // With host variables
                val hostVars = mapOf("user" to mapOf("name" to "Alice"))
                val hostResult = KodiScript.run(""""Welcome, ${"$"}{user.name}!"""", hostVars)
                assertFalse(hostResult.hasErrors, "Host errors: ${hostResult.errors}")
                assertEquals("Welcome, Alice!", hostResult.value)

                // Null value in template
                val nullResult =
                        KodiScript.run(
                                """
            let x = null
            "Value is ${"$"}{x}"
        """.trimIndent()
                        )
                assertFalse(nullResult.hasErrors, "Null errors: ${nullResult.errors}")
                assertEquals("Value is null", nullResult.value)

                // Plain string (no templates)
                val plainResult = KodiScript.run(""""Hello World"""")
                assertFalse(plainResult.hasErrors)
                assertEquals("Hello World", plainResult.value)

                // Escaped dollar sign
                val escapeResult = KodiScript.run(""""Price is \$100"""")
                assertFalse(escapeResult.hasErrors, "Escape errors: ${escapeResult.errors}")
                assertEquals("Price is $100", escapeResult.value)
        }

        @Test
        fun `test date time functions`() {
                // now() returns a timestamp
                val nowResult = KodiScript.run("now()")
                assertFalse(nowResult.hasErrors)
                val timestamp = nowResult.value as Double
                assertTrue(timestamp > 1700000000000) // After Nov 2023

                // date() returns YYYY-MM-DD format
                val dateResult = KodiScript.run("date()")
                assertFalse(dateResult.hasErrors)
                val dateStr = dateResult.value as String
                assertEquals(10, dateStr.length)
                assertEquals('-', dateStr[4])

                // time() returns HH:MM:SS format
                val timeResult = KodiScript.run("time()")
                assertFalse(timeResult.hasErrors)
                val timeStr = timeResult.value as String
                assertEquals(8, timeStr.length)
                assertEquals(':', timeStr[2])

                // datetime() returns ISO format
                val datetimeResult = KodiScript.run("datetime()")
                assertFalse(datetimeResult.hasErrors)
                val dtStr = datetimeResult.value as String
                assertTrue(dtStr.contains("T"))

                // year(), month(), day()
                val yearResult = KodiScript.run("year()")
                assertFalse(yearResult.hasErrors)
                assertTrue((yearResult.value as Double) >= 2024)

                val monthResult = KodiScript.run("month()")
                assertFalse(monthResult.hasErrors)
                val month = monthResult.value as Double
                assertTrue(month in 1.0..12.0)

                val dayResult = KodiScript.run("day()")
                assertFalse(dayResult.hasErrors)
                val day = dayResult.value as Double
                assertTrue(day in 1.0..31.0)

                // hour(), minute(), second()
                val hourResult = KodiScript.run("hour()")
                assertFalse(hourResult.hasErrors)
                val hour = hourResult.value as Double
                assertTrue(hour in 0.0..23.0)

                val minuteResult = KodiScript.run("minute()")
                assertFalse(minuteResult.hasErrors)
                val minute = minuteResult.value as Double
                assertTrue(minute in 0.0..59.0)

                val secondResult = KodiScript.run("second()")
                assertFalse(secondResult.hasErrors)
                val second = secondResult.value as Double
                assertTrue(second in 0.0..59.0)

                // dayOfWeek() returns 0-6
                val dowResult = KodiScript.run("dayOfWeek()")
                assertFalse(dowResult.hasErrors)
                val dow = dowResult.value as Double
                assertTrue(dow in 0.0..6.0)
        }

        @Test
        fun `test date parsing and formatting`() {
                // timestamp() parses date string
                val tsResult = KodiScript.run("""timestamp("2024-12-25")""")
                assertFalse(tsResult.hasErrors)
                val ts = tsResult.value as Double
                assertTrue(ts > 0)

                // formatDate() formats timestamp
                val formatResult =
                        KodiScript.run(
                                """
                        let ts = timestamp("2024-12-25")
                        formatDate(ts, "DD/MM/YYYY")
                """.trimIndent()
                        )
                assertFalse(formatResult.hasErrors)
                assertEquals("25/12/2024", formatResult.value)

                // Extract components from specific date
                val yearResult = KodiScript.run("""year(timestamp("2024-12-25"))""")
                assertFalse(yearResult.hasErrors)
                assertEquals(2024.0, yearResult.value)

                val monthResult = KodiScript.run("""month(timestamp("2024-12-25"))""")
                assertFalse(monthResult.hasErrors)
                assertEquals(12.0, monthResult.value)

                val dayResult = KodiScript.run("""day(timestamp("2024-12-25"))""")
                assertFalse(dayResult.hasErrors)
                assertEquals(25.0, dayResult.value)
        }

        @Test
        fun `test date arithmetic`() {
                // addDays()
                val addDaysResult =
                        KodiScript.run(
                                """
                        let ts = timestamp("2024-01-01")
                        let nextWeek = addDays(ts, 7)
                        day(nextWeek)
                """.trimIndent()
                        )
                assertFalse(addDaysResult.hasErrors)
                assertEquals(8.0, addDaysResult.value)

                // diffDays()
                val diffResult =
                        KodiScript.run(
                                """
                        let ts1 = timestamp("2024-01-01")
                        let ts2 = timestamp("2024-01-08")
                        diffDays(ts1, ts2)
                """.trimIndent()
                        )
                assertFalse(diffResult.hasErrors)
                assertEquals(7.0, diffResult.value)

                // addHours()
                val addHoursResult =
                        KodiScript.run(
                                """
                        let ts = now()
                        let later = addHours(ts, 24)
                        diffDays(ts, later)
                """.trimIndent()
                        )
                assertFalse(addHoursResult.hasErrors)
                assertEquals(1.0, addHoursResult.value)
        }
        @Test
        fun `test single quote strings`() {
                val result = KodiScript.run("let s = 'hello world'; s")
                assertFalse(result.hasErrors)
                assertEquals("hello world", result.value)

                val escaped = KodiScript.run("let s = 'hello \\'world\\''; s")
                assertFalse(escaped.hasErrors)
                assertEquals("hello 'world'", escaped.value)

                val mixed = KodiScript.run("let s = \"hello 'world'\"; s")
                assertFalse(mixed.hasErrors)
                assertEquals("hello 'world'", mixed.value)

                val mixed2 = KodiScript.run("let s = 'hello \"world\"'; s")
                assertFalse(mixed2.hasErrors)
                assertEquals("hello \"world\"", mixed2.value)
        }

        @Test
        fun `test multiline object literals`() {
                // Test multiline without commas (using newlines)
                val result =
                        KodiScript.run(
                                """
                        let obj = {
                            name: "Kodi"
                            age: 10
                            active: true
                        }
                        obj.name
                        """
                        )
                assertFalse(result.hasErrors, "Errors: ${result.errors}")
                assertEquals("Kodi", result.value)

                // Test multiline with mixed commas and newlines
                val resultMixed =
                        KodiScript.run(
                                """
                        let obj = {
                            name: "Kodi",
                            age: 10
                            active: true,
                        }
                        obj.age
                        """
                        )
                assertFalse(resultMixed.hasErrors, "Errors: ${resultMixed.errors}")
                assertEquals(10.0, resultMixed.value)

                // Test multiline with empty lines
                val resultEmptyLines =
                        KodiScript.run(
                                """
                        let obj = {
                            
                            name: "Kodi"
                            
                            age: 10
                        }
                        obj.age
                        """
                        )
                assertFalse(resultEmptyLines.hasErrors, "Errors: ${resultEmptyLines.errors}")
                assertEquals(10.0, resultEmptyLines.value)
        }

        @Test
        fun `test backtick strings`() {
                // Basic backtick string
                val basic = KodiScript.run("let s = `hello world`; s")
                assertFalse(basic.hasErrors)
                assertEquals("hello world", basic.value)

                // Multi-line backtick string
                val multiline =
                        KodiScript.run(
                                """
                        let s = `hello
                        world`;
                        s
                        """
                        )
                assertFalse(multiline.hasErrors)
                // Note: indentation in the kotlin multiline string is preserved in the script
                // source
                // but the backtick string captures the newline and indentation inside it.
                // The expected string depends on how the test source is formatted.
                // To avoid fragility with indentation, we just check it contains the newline and
                // words.
                val valStr = multiline.value as String
                assertTrue(valStr.contains("hello"))
                assertTrue(valStr.contains("world"))
                assertTrue(valStr.contains("\n"))

                // Interpolation in backticks (should work as normal)
                val interpolation =
                        KodiScript.run(
                                """
                        let name = "Kodi"
                        `Hello ${"$"}{name}`
                        """
                        )
                assertFalse(interpolation.hasErrors)
                assertEquals("Hello Kodi", interpolation.value)

                // Backticks with quotes inside
                val quotes = KodiScript.run("`result: \"success\", 'ok'`")
                assertFalse(quotes.hasErrors)
                assertEquals("result: \"success\", 'ok'", quotes.value)
        }
}
