package com.kodi.script.natives

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

typealias NativeFunc = (List<Any?>) -> Any?

/** Registry of native functions for KodiScript. */
class NativeFunctions {
    private val functions = mutableMapOf<String, NativeFunc>()

    init {
        registerBuiltins()
    }

    fun get(name: String): NativeFunc? = functions[name]

    fun register(name: String, fn: NativeFunc) {
        functions[name] = fn
    }

    private fun registerBuiltins() {
        // String functions
        functions["toString"] = ::nativeToString
        functions["toNumber"] = ::nativeToNumber
        functions["length"] = ::nativeLength
        functions["substring"] = ::nativeSubstring
        functions["toUpperCase"] = ::nativeToUpperCase
        functions["toLowerCase"] = ::nativeToLowerCase

        // JSON functions
        functions["jsonParse"] = ::nativeJsonParse
        functions["jsonStringify"] = ::nativeJsonStringify

        // Base64 functions
        functions["base64Encode"] = ::nativeBase64Encode
        functions["base64Decode"] = ::nativeBase64Decode

        // URL functions
        functions["urlEncode"] = ::nativeUrlEncode
        functions["urlDecode"] = ::nativeUrlDecode

        // Type functions
        functions["typeOf"] = ::nativeTypeOf
        functions["isNull"] = ::nativeIsNull
    }

    // String functions

    private fun nativeToString(args: List<Any?>): String {
        require(args.size == 1) { "toString requires 1 argument" }
        return args[0]?.toString() ?: "null"
    }

    private fun nativeToNumber(args: List<Any?>): Double {
        require(args.size == 1) { "toNumber requires 1 argument" }
        return when (val arg = args[0]) {
            is Double -> arg
            is Int -> arg.toDouble()
            is Long -> arg.toDouble()
            is String -> arg.toDoubleOrNull()
                            ?: throw IllegalArgumentException("cannot convert '$arg' to number")
            else ->
                    throw IllegalArgumentException(
                            "cannot convert ${arg?.javaClass?.simpleName} to number"
                    )
        }
    }

    private fun nativeLength(args: List<Any?>): Double {
        require(args.size == 1) { "length requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("length requires a string argument")
        return str.length.toDouble()
    }

    private fun nativeSubstring(args: List<Any?>): String {
        require(args.size in 2..3) { "substring requires 2 or 3 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "substring requires a string as first argument"
                        )
        val start =
                (args[1] as? Double)?.toInt()
                        ?: throw IllegalArgumentException(
                                "substring requires a number as second argument"
                        )

        val startIdx = maxOf(0, start)
        if (startIdx >= str.length) return ""

        return if (args.size == 3) {
            val end =
                    (args[2] as? Double)?.toInt()
                            ?: throw IllegalArgumentException(
                                    "substring requires a number as third argument"
                            )
            val endIdx = minOf(str.length, end)
            str.substring(startIdx, endIdx)
        } else {
            str.substring(startIdx)
        }
    }

    private fun nativeToUpperCase(args: List<Any?>): String {
        require(args.size == 1) { "toUpperCase requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("toUpperCase requires a string argument")
        return str.uppercase()
    }

    private fun nativeToLowerCase(args: List<Any?>): String {
        require(args.size == 1) { "toLowerCase requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("toLowerCase requires a string argument")
        return str.lowercase()
    }

    // JSON functions (simplified - would use a JSON library in production)

    private fun nativeJsonParse(args: List<Any?>): Any? {
        require(args.size == 1) { "jsonParse requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("jsonParse requires a string argument")
        // Simple JSON parsing (would use Jackson/Gson in production)
        return parseSimpleJson(str)
    }

    private fun nativeJsonStringify(args: List<Any?>): String {
        require(args.size == 1) { "jsonStringify requires 1 argument" }
        return stringifyValue(args[0])
    }

    // Base64 functions

    private fun nativeBase64Encode(args: List<Any?>): String {
        require(args.size == 1) { "base64Encode requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("base64Encode requires a string argument")
        return Base64.getEncoder().encodeToString(str.toByteArray())
    }

    private fun nativeBase64Decode(args: List<Any?>): String {
        require(args.size == 1) { "base64Decode requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("base64Decode requires a string argument")
        return String(Base64.getDecoder().decode(str))
    }

    // URL functions

    private fun nativeUrlEncode(args: List<Any?>): String {
        require(args.size == 1) { "urlEncode requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("urlEncode requires a string argument")
        return URLEncoder.encode(str, Charsets.UTF_8)
    }

    private fun nativeUrlDecode(args: List<Any?>): String {
        require(args.size == 1) { "urlDecode requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("urlDecode requires a string argument")
        return URLDecoder.decode(str, Charsets.UTF_8)
    }

    // Type functions

    private fun nativeTypeOf(args: List<Any?>): String {
        require(args.size == 1) { "typeOf requires 1 argument" }
        return when (args[0]) {
            null -> "null"
            is String -> "string"
            is Double, is Int, is Long -> "number"
            is Boolean -> "boolean"
            is Map<*, *> -> "object"
            else -> "unknown"
        }
    }

    private fun nativeIsNull(args: List<Any?>): Boolean {
        require(args.size == 1) { "isNull requires 1 argument" }
        return args[0] == null
    }

    // Helper functions

    private fun parseSimpleJson(str: String): Any? {
        val trimmed = str.trim()
        return when {
            trimmed == "null" -> null
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                    trimmed.substring(1, trimmed.length - 1)
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> throw IllegalArgumentException("invalid JSON: $str")
        }
    }

    private fun stringifyValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"$value\""
            is Boolean -> value.toString()
            is Number -> value.toString()
            is Map<*, *> -> {
                val entries =
                        value.entries.joinToString(",") { (k, v) -> "\"$k\":${stringifyValue(v)}" }
                "{$entries}"
            }
            is List<*> -> {
                val items = value.joinToString(",") { stringifyValue(it) }
                "[$items]"
            }
            else -> "\"$value\""
        }
    }
}
