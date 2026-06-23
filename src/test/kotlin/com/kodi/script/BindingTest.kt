package com.kodi.script

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

// Test classes
data class Address(val city: String, val country: String)

class User(val name: String, val age: Int, val address: Address) {
    fun sayHello(): String = "Hello, I'm $name"

    fun fetchAge(): Int = age

    fun greet(greeting: String): String = "$greeting, $name!"

    fun fetchAddress(): Address = address
}

class Calculator {
    fun add(a: Double, b: Double): Double = a + b

    fun multiply(x: Int, y: Int): Int = x * y

    fun divide(a: Double, b: Double): Double {
        if (b == 0.0) return 0.0
        return a / b
    }
}

class BindingTest {

    @Test
    fun `bind field access`() {
        val user = User("Alice", 30, Address("Paris", "France"))

        val result = KodiScript.builder("user.name").bind("user", user).execute()

        assertEquals("Alice", result.value)
    }

    @Test
    fun `bind method call`() {
        val user = User("Bob", 25, Address("London", "UK"))

        val result = KodiScript.builder("user.sayHello()").bind("user", user).execute()

        assertEquals("Hello, I'm Bob", result.value)
    }

    @Test
    fun `bind method with arguments`() {
        val user = User("Charlie", 28, Address("Berlin", "Germany"))

        val result = KodiScript.builder("user.greet(\"Hi\")").bind("user", user).execute()

        assertEquals("Hi, Charlie!", result.value)
    }

    @Test
    fun `bind nested objects`() {
        val user = User("David", 35, Address("Tokyo", "Japan"))

        val result = KodiScript.builder("user.address.city").bind("user", user).execute()

        assertEquals("Tokyo", result.value)
    }

    @Test
    fun `bind method chaining`() {
        val user = User("Emily", 32, Address("Madrid", "Spain"))

        val result = KodiScript.builder("user.fetchAddress().city").bind("user", user).execute()

        assertEquals("Madrid", result.value)
    }

    @Test
    fun `bind numeric conversion`() {
        val calc = Calculator()

        val result = KodiScript.builder("calc.add(10, 20)").bind("calc", calc).execute()

        assertEquals(30.0, result.value)
    }

    @Test
    fun `bind int return converts to double`() {
        val user = User("Frank", 42, Address("Rome", "Italy"))

        val result = KodiScript.builder("user.fetchAge()").bind("user", user).execute()

        assertEquals(42.0, result.value)
    }

    @Test
    fun `bind multiple objects`() {
        val user = User("Grace", 27, Address("Amsterdam", "Netherlands"))
        val calc = Calculator()

        val script =
                """
            let greeting = user.sayHello()
            let sum = calc.add(5, 3)
            greeting + " " + sum
        """.trimIndent()

        val result = KodiScript.builder(script).bind("user", user).bind("calc", calc).execute()

        assertEquals("Hello, I'm Grace 8", result.value)
    }

    @Test
    fun `bind non-existent property throws error`() {
        val user = User("Henry", 31, Address("Prague", "Czech Republic"))

        val result = KodiScript.builder("user.nonExistent").bind("user", user).execute()

        assertTrue(result.hasErrors)
        assertTrue(result.errors.first().contains("nonExistent"))
    }

    @Test
    fun `bind with variables`() {
        val calc = Calculator()

        val script =
                """
            let x = 10
            let y = 5
            calc.add(x, y)
        """.trimIndent()

        val result = KodiScript.builder(script).bind("calc", calc).execute()

        assertEquals(15.0, result.value)
    }

    @Test
    fun `bind in loop`() {
        val calc = Calculator()

        val script =
                """
            let numbers = [1, 2, 3, 4, 5]
            let sum = 0
            for (n in numbers) {
                sum = calc.add(sum, n)
            }
            sum
        """.trimIndent()

        val result = KodiScript.builder(script).bind("calc", calc).execute()

        assertEquals(15.0, result.value)
    }

    @Test
    fun `bind method with multiple arguments`() {
        val calc = Calculator()

        val result = KodiScript.builder("calc.multiply(6, 7)").bind("calc", calc).execute()

        assertEquals(42.0, result.value)
    }
}
