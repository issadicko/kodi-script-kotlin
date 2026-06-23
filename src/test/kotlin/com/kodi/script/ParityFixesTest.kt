package com.kodi.script

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Locks in fixes for the print-capture bug, the JSON parser/serializer rewrite,
 * and the silent-print option.
 */
class ParityFixesTest {

    @Test
    fun `print inside a function is captured in output`() {
        val result =
                KodiScript.builder(
                                """
            let f = fn() {
                print("inside")
            }
            f()
            print("outside")
        """
                        )
                        .withSilentPrint(true)
                        .execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("inside", "outside"), result.output)
    }

    @Test
    fun `silent print still captures output`() {
        val result = KodiScript.builder("""print("a")""").withSilentPrint(true).execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("a"), result.output)
    }

    @Test
    fun `jsonParse handles nested objects`() {
        val result =
                KodiScript.builder(
                                """print(jsonStringify(jsonParse("{\"a\": {\"b\": 1}}")))"""
                        )
                        .withSilentPrint(true)
                        .execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("""{"a":{"b":1}}"""), result.output)
    }

    @Test
    fun `jsonParse preserves commas inside strings`() {
        val result =
                KodiScript.builder("""print(jsonStringify(jsonParse("{\"a\": \"x,y\"}")))""")
                        .withSilentPrint(true)
                        .execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("""{"a":"x,y"}"""), result.output)
    }

    @Test
    fun `jsonParse handles mixed arrays and escapes`() {
        val result =
                KodiScript.builder(
                                """print(jsonStringify(jsonParse("[1, \"two\", true, null, [3]]")))"""
                        )
                        .withSilentPrint(true)
                        .execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("""[1,"two",true,null,[3]]"""), result.output)
    }

    @Test
    fun `jsonStringify escapes special characters`() {
        val result =
                KodiScript.builder("""print(jsonStringify(jsonParse("{\"k\": \"a\\nb\"}")))""")
                        .withSilentPrint(true)
                        .execute()
        assertFalse(result.hasErrors)
        assertEquals(listOf("""{"k":"a\nb"}"""), result.output)
    }

    @Test
    fun `jsonParse rejects malformed input`() {
        val result = KodiScript.builder("""jsonParse("{\"a\": }")""").withSilentPrint(true).execute()
        assertTrue(result.hasErrors)
    }
}
