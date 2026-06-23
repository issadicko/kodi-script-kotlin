package com.kodi.script

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Regression tests for the new language features (lots 1-4). */
class FeaturesTest {

    private fun out(src: String): List<String> {
        val result = KodiScript.builder(src).withSilentPrint(true).execute()
        assertFalse(result.hasErrors, "unexpected errors: ${result.errors}")
        return result.output
    }

    // ---- Lot 1: break / continue / compound assignment / ++ -- ----

    @Test
    fun `break exits for loop`() {
        assertEquals(
                listOf("3"),
                out("let s=0\nfor (i in [1,2,3,4,5]) {\n if (i==3) { break }\n s = s + i\n}\nprint(s)")
        )
    }

    @Test
    fun `continue skips iteration`() {
        assertEquals(
                listOf("8"),
                out("let s=0\nfor (i in [1,2,3,4]) {\n if (i==2) { continue }\n s += i\n}\nprint(s)")
        )
    }

    @Test
    fun `break exits while loop`() {
        assertEquals(
                listOf("5"),
                out("let i=0\nwhile (true) {\n i++\n if (i>=5) { break }\n}\nprint(i)")
        )
    }

    @Test
    fun `compound assignment operators`() {
        assertEquals(listOf("2"), out("let x=10\nx += 5\nx -= 2\nx *= 2\nx /= 13\nprint(x)"))
    }

    @Test
    fun `increment and decrement`() {
        assertEquals(listOf("6"), out("let n=5\nn++\nn++\nn--\nprint(n)"))
    }

    @Test
    fun `nested break only exits inner loop`() {
        assertEquals(
                listOf("2"),
                out(
                        "let c=0\nfor (i in [1,2]) {\n for (j in [1,2,3]) {\n  if (j==2) { break }\n  c++\n }\n}\nprint(c)"
                )
        )
    }

    // ---- Lot 2: method-call syntax + de-hardcoded builtins ----

    @Test
    fun `method call on string`() {
        assertEquals(listOf("HELLO"), out("""print("hello".toUpperCase())"""))
    }

    @Test
    fun `chained method calls`() {
        assertEquals(listOf("hi"), out("""print("  Hi ".trim().toLowerCase())"""))
    }

    @Test
    fun `method call on array`() {
        assertEquals(listOf("3"), out("print([1,2,3].size())"))
        assertEquals(listOf("a-b"), out("""print(["a","b"].join("-"))"""))
    }

    @Test
    fun `higher order function via method syntax`() {
        assertEquals(listOf("[2, 4, 6]"), out("print([1,2,3].map(fn(x){ x*2 }))"))
    }

    @Test
    fun `object stored function called as method`() {
        assertEquals(listOf("hi"), out("""let o = {greet: fn(){ "hi" }}""" + "\n" + "print(o.greet())"))
    }

    @Test
    fun `builtins are overridable`() {
        val result =
                KodiScript.builder("let print = fn(x){ x }\nprint(\"hidden\")")
                        .withSilentPrint(true)
                        .execute()
        assertFalse(result.hasErrors)
        assertEquals(emptyList<String>(), result.output)
    }

    @Test
    fun `bare builtins still work`() {
        assertEquals(
                listOf("X", "[2, 3]"),
                out("""print(toUpperCase("x"))""" + "\n" + "print(map([1,2], fn(x){ x+1 }))")
        )
    }

    // ---- Lot 3: stdlib expansion ----

    @Test
    fun `range sum avg`() {
        assertEquals(listOf("[0, 1, 2, 3]"), out("print(range(4))"))
        assertEquals(listOf("[2, 3, 4]"), out("print(range(2,5))"))
        assertEquals(listOf("10"), out("print(sum([1,2,3,4]))"))
        assertEquals(listOf("4"), out("print(avg([2,4,6]))"))
    }

    @Test
    fun `unique flatten push concat`() {
        assertEquals(listOf("[1, 2, 3]"), out("print(unique([1,1,2,3,3]))"))
        assertEquals(listOf("[1, 2, 3]"), out("print(flatten([[1,2],[3]]))"))
        assertEquals(listOf("[1, 2, 3, 4]"), out("print(push([1,2],3,4))"))
        assertEquals(listOf("[1, 2, 3]"), out("print(concat([1],[2,3]))"))
    }

    @Test
    fun `object keys values has - keys sorted for determinism`() {
        assertEquals(listOf("[a, b, c]"), out("print(keys({b:2, a:1, c:3}))"))
        assertEquals(listOf("[1, 2]"), out("print(values({b:2, a:1}))"))
        assertEquals(listOf("true"), out("""print(has({a:1}, "a"))"""))
        assertEquals(listOf("false"), out("""print(has({a:1}, "x"))"""))
        assertEquals(listOf("true"), out("print(has([1,2,3], 2))"))
    }

    @Test
    fun `parseInt and parseFloat`() {
        assertEquals(listOf("3"), out("""print(parseInt("3.9"))"""))
        assertEquals(listOf("3.14"), out("""print(parseFloat("3.14"))"""))
    }

    @Test
    fun `new natives chain via method syntax`() {
        assertEquals(listOf("6"), out("print([3,1,2,1].unique().sum())"))
    }

    // ---- B: named functions, ternary, else-if, block comments ----

    @Test
    fun `named function and recursion`() {
        assertEquals(
                listOf("120"),
                out("fn fact(n) {\n if (n <= 1) { return 1 }\n return n * fact(n - 1)\n}\nprint(fact(5))")
        )
    }

    @Test
    fun `ternary conditional`() {
        assertEquals(listOf("big"), out("let x = 5\nprint(x > 3 ? \"big\" : \"small\")"))
        assertEquals(
                listOf("two"),
                out("let n = 2\nprint(n == 1 ? \"one\" : n == 2 ? \"two\" : \"many\")")
        )
    }

    @Test
    fun `else if chain`() {
        assertEquals(
                listOf("B"),
                out(
                        "let g = 85\nif (g >= 90) { print(\"A\") } else if (g >= 80) { print(\"B\") } else { print(\"C\") }"
                )
        )
    }

    @Test
    fun `block comments`() {
        assertEquals(listOf("8"), out("let x = 5 /* inline */ + 3\n/* multi\nline */\nprint(x)"))
    }

    // ---- C: try/catch + runtime error positions ----

    @Test
    fun `try catch binds error and continues`() {
        assertEquals(
                listOf("caught", "after"),
                out(
                        "let r = \"none\"\ntry {\n let x = boom\n} catch (e) {\n r = \"caught\"\n}\nprint(r)\nprint(\"after\")"
                )
        )
    }

    @Test
    fun `catch without variable`() {
        assertEquals(listOf("handled"), out("try {\n let y = boom\n} catch {\n print(\"handled\")\n}"))
    }

    @Test
    fun `return inside try`() {
        assertEquals(
                listOf("5", "-1"),
                out(
                        "fn safeDiv(a, b) {\n try {\n  return a / b\n } catch (e) {\n  return -1\n }\n}\nprint(safeDiv(10, 2))\nprint(safeDiv(10, 0))"
                )
        )
    }

    @Test
    fun `runtime error has position`() {
        val result = KodiScript.builder("let a = 1\nlet b = undefinedThing").execute()
        assertTrue(result.hasErrors)
        assertTrue(result.errors[0].contains("line 2"), "expected position, got ${result.errors}")
    }

    // ---- D: recursion guard (robustness) ----

    @Test
    fun `recursion guard stops unbounded recursion`() {
        val result = KodiScript.builder("fn loop() {\n return loop()\n}\nloop()").execute()
        assertTrue(result.hasErrors)
        assertEquals(ErrorKind.RUNTIME, result.errorKind)
    }

    @Test
    fun `deep but finite recursion works`() {
        assertEquals(
                listOf("5050"),
                out("fn sum(n) {\n if (n == 0) { return 0 }\n return n + sum(n - 1)\n}\nprint(sum(100))")
        )
    }

    // ---- E: some/every/flatMap, regex, spread, destructuring ----

    @Test
    fun `some every flatMap`() {
        assertEquals(listOf("true"), out("print(some([1,2,3], fn(x){ x > 2 }))"))
        assertEquals(listOf("true"), out("print(every([2,4], fn(x){ x % 2 == 0 }))"))
        assertEquals(listOf("[1, 1, 2, 2]"), out("print(flatMap([1,2], fn(x){ [x, x] }))"))
    }

    @Test
    fun `regex match and replace`() {
        assertEquals(listOf("true"), out("""print(regexMatch("abc123", "[0-9]+"))"""))
        assertEquals(listOf("aXbX"), out("""print(regexReplace("a1b2", "[0-9]", "X"))"""))
    }

    @Test
    fun `spread in arrays and calls`() {
        assertEquals(listOf("[1, 2, 3]"), out("let a = [1,2]\nprint([...a, 3])"))
        assertEquals(listOf("6"), out("fn add(x,y,z){ return x+y+z }\nprint(add(...[1,2,3]))"))
    }

    @Test
    fun `array and object destructuring`() {
        assertEquals(listOf("10", "20"), out("let [a, b] = [10, 20]\nprint(a)\nprint(b)"))
        assertEquals(
                listOf("Bob", "25"),
                out("""let {name, age} = {name: "Bob", age: 25}""" + "\nprint(name)\nprint(age)")
        )
    }

    // ---- Lot 4: output sink + typed errors ----

    @Test
    fun `output sink receives and captures`() {
        val sink = mutableListOf<String>()
        val result =
                KodiScript.builder("print(\"a\")\nprint(\"b\")").withOutput { sink.add(it) }.execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("a", "b"), sink)
        assertEquals(listOf("a", "b"), result.output) // still captured
    }

    @Test
    fun `typed error kinds`() {
        assertEquals(
                ErrorKind.TIMEOUT,
                KodiScript.builder("while (true) { let x = 1 }").withTimeout(50).execute().errorKind
        )
        assertEquals(
                ErrorKind.MAX_OPERATIONS,
                KodiScript.builder("while (true) { let x = 1 }")
                        .withMaxOperations(100)
                        .execute()
                        .errorKind
        )
        assertEquals(ErrorKind.RUNTIME, KodiScript.builder("undefinedVar + 1").execute().errorKind)
        assertEquals(ErrorKind.PARSE, KodiScript.builder("let = = =").execute().errorKind)
        assertEquals(ErrorKind.NONE, KodiScript.builder("let x = 5").execute().errorKind)
    }
}
