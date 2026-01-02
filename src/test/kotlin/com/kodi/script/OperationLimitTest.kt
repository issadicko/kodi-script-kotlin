package com.kodi.script

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class OperationLimitTest {

    @Test
    fun `simple script completes within limit`() {
        val result =
                KodiScript.builder(
                                """
            let x = 1
            let y = 2
            x + y
        """
                        )
                        .withMaxOperations(100)
                        .execute()

        assertEquals(3.0, result.value)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `exceeds operation limit`() {
        val result =
                KodiScript.builder(
                                """
            let sum = 0
            for (i in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) {
                sum = sum + i
            }
            sum
        """
                        )
                        .withMaxOperations(5)
                        .execute()

        assertTrue(result.hasErrors)
        assertTrue(result.errors.first().contains("max operations exceeded"))
    }

    @Test
    fun `infinite loop protection`() {
        val largeArray = (1..10000).map { it.toDouble() }

        val result =
                KodiScript.builder(
                                """
            let sum = 0
            for (i in arr) {
                sum = sum + i
            }
            sum
        """
                        )
                        .withVariable("arr", largeArray)
                        .withMaxOperations(100)
                        .execute()

        assertTrue(result.hasErrors)
    }

    @Test
    fun `nested loops respect limit`() {
        val result =
                KodiScript.builder(
                                """
            let count = 0
            for (i in [1, 2, 3, 4, 5]) {
                for (j in [1, 2, 3, 4, 5]) {
                    count = count + 1
                }
            }
            count
        """
                        )
                        .withMaxOperations(10)
                        .execute()

        assertTrue(result.hasErrors)
    }

    @Test
    fun `no limit by default`() {
        val result =
                KodiScript.builder(
                                """
            let sum = 0
            for (i in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) {
                sum = sum + i
            }
            sum
        """
                        )
                        .execute()

        assertEquals(55.0, result.value)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `zero means unlimited`() {
        val result =
                KodiScript.builder(
                                """
            let sum = 0
            for (i in [1, 2, 3, 4, 5]) {
                sum = sum + i
            }
            sum
        """
                        )
                        .withMaxOperations(0)
                        .execute()

        assertEquals(15.0, result.value)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `operation limit with bindings`() {
        data class Counter(val value: Int)
        val counter = Counter(5)

        val result =
                KodiScript.builder(
                                """
            let sum = 0
            for (i in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) {
                sum = sum + counter.value
            }
            sum
        """
                        )
                        .bind("counter", counter)
                        .withMaxOperations(5)
                        .execute()

        assertTrue(result.hasErrors)
    }
}
