package com.kodi.script

import kotlin.system.measureTimeMillis
import org.junit.jupiter.api.Test

class BenchmarkTest {

    @Test
    fun `benchmark parsing performance`() {
        val script =
                """
            let factorial = fn(n) {
                if (n <= 1) {
                    return 1
                }
                return n * factorial(n - 1)
            }
            print(factorial(10))
        """.trimIndent()

        val iterations = 1000
        val time = measureTimeMillis {
            repeat(iterations) { KodiScript.builder(script).withCache(false).execute() }
        }

        println(
                "Parsing $iterations iterations (no cache): ${time}ms (${time.toDouble() / iterations}ms per iteration)"
        )
    }

    @Test
    fun `benchmark execution performance`() {
        val script =
                """
            let sum = 0
            for (i in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]) {
                sum = sum + i
            }
            print(sum)
        """.trimIndent()

        val iterations = 10000
        val time = measureTimeMillis { repeat(iterations) { KodiScript.run(script) } }

        println(
                "Execution $iterations iterations (with cache): ${time}ms (${time.toDouble() / iterations}ms per iteration)"
        )
    }

    @Test
    fun `benchmark cache effectiveness`() {
        val script =
                """
            let add = fn(a, b) { return a + b }
            print(add(5, 7))
        """.trimIndent()

        val iterations = 5000

        // Warm up
        repeat(100) { KodiScript.run(script) }

        // With cache
        val cachedTime = measureTimeMillis { repeat(iterations) { KodiScript.run(script) } }

        // Without cache
        val uncachedTime = measureTimeMillis {
            repeat(iterations) { KodiScript.builder(script).withCache(false).execute() }
        }

        println(
                "With cache: ${cachedTime}ms (${cachedTime.toDouble() / iterations}ms per iteration)"
        )
        println(
                "Without cache: ${uncachedTime}ms (${uncachedTime.toDouble() / iterations}ms per iteration)"
        )
        println("Speedup: ${String.format("%.2f", uncachedTime.toDouble() / cachedTime)}x")
    }
}
