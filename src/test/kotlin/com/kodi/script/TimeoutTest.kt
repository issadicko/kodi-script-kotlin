package com.kodi.script

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class TimeoutTest {

    @Test
    fun `simple script completes within timeout`() {
        val result =
                KodiScript.builder(
                                """
            let x = 1
            let y = 2
            x + y
        """
                        )
                        .withTimeout(5000)
                        .execute()

        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `long loop exceeds timeout`() {
        val largeArray = (1..1000000).map { it.toDouble() }

        val start = System.currentTimeMillis()
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
                        .withTimeout(50) // 50ms timeout
                        .execute()
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result.hasErrors)
        assertTrue(result.errors.first().contains("execution timeout"))
        assertTrue(elapsed < 200, "Should stop execution quickly")
    }

    @Test
    fun `nested loops respect timeout`() {
        val result =
                KodiScript.builder(
                                """
            let count = 0
            for (i in [1, 2, 3]) {
                for (j in [1, 2, 3]) {
                    count = count + 1
                    // Simulated work
                    for (k in [1, 2, 3, 4, 5]) {
                        count = count + 1
                    }
                }
            }
            count
        """
                        )
                        .withTimeout(5000)
                        .execute()

        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `no timeout by default`() {
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
                        .execute()

        assertTrue(result.errors.isEmpty())
    }
}
