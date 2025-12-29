package com.kodi.script.natives

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

typealias NativeFunc = (List<Any?>) -> Any?

/** Registry of native functions for KodiScript. */
class NativeFunctions private constructor() {
    private val functions = mutableMapOf<String, NativeFunc>()

    companion object {
        /** Shared singleton instance for built-in functions. */
        val shared: NativeFunctions by lazy { NativeFunctions().apply { registerBuiltins() } }

        /** Create a new instance with built-in functions (for custom functions). */
        fun withBuiltins(): NativeFunctions {
            return NativeFunctions().apply { copyFrom(shared) }
        }
    }

    fun get(name: String): NativeFunc? = functions[name]

    fun register(name: String, fn: NativeFunc) {
        functions[name] = fn
    }

    /** Copy all functions from another instance. */
    private fun copyFrom(other: NativeFunctions) {
        functions.putAll(other.functions)
    }

    private fun registerBuiltins() {
        // String functions
        functions["toString"] = ::nativeToString
        functions["toNumber"] = ::nativeToNumber
        functions["length"] = ::nativeLength
        functions["substring"] = ::nativeSubstring
        functions["toUpperCase"] = ::nativeToUpperCase
        functions["toLowerCase"] = ::nativeToLowerCase
        functions["trim"] = ::nativeTrim
        functions["split"] = ::nativeSplit
        functions["join"] = ::nativeJoin
        functions["replace"] = ::nativeReplace
        functions["contains"] = ::nativeContains
        functions["startsWith"] = ::nativeStartsWith
        functions["endsWith"] = ::nativeEndsWith
        functions["indexOf"] = ::nativeIndexOf

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
        functions["isNumber"] = ::nativeIsNumber
        functions["isString"] = ::nativeIsString
        functions["isBool"] = ::nativeIsBool

        // Math functions
        functions["abs"] = ::nativeAbs
        functions["floor"] = ::nativeFloor
        functions["ceil"] = ::nativeCeil
        functions["round"] = ::nativeRound
        functions["min"] = ::nativeMin
        functions["max"] = ::nativeMax
        functions["pow"] = ::nativePow
        functions["sqrt"] = ::nativeSqrt
        functions["sin"] = ::nativeSin
        functions["cos"] = ::nativeCos
        functions["tan"] = ::nativeTan
        functions["log"] = ::nativeLog
        functions["log10"] = ::nativeLog10
        functions["exp"] = ::nativeExp

        // Random functions
        functions["random"] = ::nativeRandom
        functions["randomInt"] = ::nativeRandomInt
        functions["randomUUID"] = ::nativeRandomUUID

        // Crypto/Hash functions
        functions["md5"] = ::nativeMd5
        functions["sha1"] = ::nativeSha1
        functions["sha256"] = ::nativeSha256

        // Array functions
        functions["sort"] = ::nativeSort
        functions["sortBy"] = ::nativeSortBy
        functions["reverse"] = ::nativeReverse
        functions["size"] = ::nativeSize
        functions["first"] = ::nativeFirst
        functions["last"] = ::nativeLast
        functions["slice"] = ::nativeSlice
    }

    // ============ String functions ============

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

    private fun nativeTrim(args: List<Any?>): String {
        require(args.size == 1) { "trim requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("trim requires a string argument")
        return str.trim()
    }

    private fun nativeSplit(args: List<Any?>): List<Any?> {
        require(args.size == 2) { "split requires 2 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "split requires a string as first argument"
                        )
        val sep =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "split requires a string as second argument"
                        )
        return str.split(sep)
    }

    private fun nativeJoin(args: List<Any?>): String {
        require(args.size == 2) { "join requires 2 arguments" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException(
                                "join requires an array as first argument"
                        )
        val sep =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "join requires a string as second argument"
                        )
        return arr.joinToString(sep) { it?.toString() ?: "null" }
    }

    private fun nativeReplace(args: List<Any?>): String {
        require(args.size == 3) { "replace requires 3 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "replace requires a string as first argument"
                        )
        val old =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "replace requires a string as second argument"
                        )
        val new =
                args[2] as? String
                        ?: throw IllegalArgumentException(
                                "replace requires a string as third argument"
                        )
        return str.replace(old, new)
    }

    private fun nativeContains(args: List<Any?>): Boolean {
        require(args.size == 2) { "contains requires 2 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "contains requires a string as first argument"
                        )
        val substr =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "contains requires a string as second argument"
                        )
        return str.contains(substr)
    }

    private fun nativeStartsWith(args: List<Any?>): Boolean {
        require(args.size == 2) { "startsWith requires 2 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "startsWith requires a string as first argument"
                        )
        val prefix =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "startsWith requires a string as second argument"
                        )
        return str.startsWith(prefix)
    }

    private fun nativeEndsWith(args: List<Any?>): Boolean {
        require(args.size == 2) { "endsWith requires 2 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "endsWith requires a string as first argument"
                        )
        val suffix =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "endsWith requires a string as second argument"
                        )
        return str.endsWith(suffix)
    }

    private fun nativeIndexOf(args: List<Any?>): Double {
        require(args.size == 2) { "indexOf requires 2 arguments" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "indexOf requires a string as first argument"
                        )
        val substr =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "indexOf requires a string as second argument"
                        )
        return str.indexOf(substr).toDouble()
    }

    // ============ JSON functions ============

    private fun nativeJsonParse(args: List<Any?>): Any? {
        require(args.size == 1) { "jsonParse requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("jsonParse requires a string argument")
        return parseSimpleJson(str)
    }

    private fun nativeJsonStringify(args: List<Any?>): String {
        require(args.size == 1) { "jsonStringify requires 1 argument" }
        return stringifyValue(args[0])
    }

    // ============ Base64 functions ============

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

    // ============ URL functions ============

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

    // ============ Type functions ============

    private fun nativeTypeOf(args: List<Any?>): String {
        require(args.size == 1) { "typeOf requires 1 argument" }
        return when (args[0]) {
            null -> "null"
            is String -> "string"
            is Double, is Int, is Long -> "number"
            is Boolean -> "boolean"
            is Map<*, *> -> "object"
            is List<*> -> "array"
            else -> "unknown"
        }
    }

    private fun nativeIsNull(args: List<Any?>): Boolean {
        require(args.size == 1) { "isNull requires 1 argument" }
        return args[0] == null
    }

    private fun nativeIsNumber(args: List<Any?>): Boolean {
        require(args.size == 1) { "isNumber requires 1 argument" }
        return args[0] is Double || args[0] is Int || args[0] is Long
    }

    private fun nativeIsString(args: List<Any?>): Boolean {
        require(args.size == 1) { "isString requires 1 argument" }
        return args[0] is String
    }

    private fun nativeIsBool(args: List<Any?>): Boolean {
        require(args.size == 1) { "isBool requires 1 argument" }
        return args[0] is Boolean
    }

    // ============ Math functions ============

    private fun nativeAbs(args: List<Any?>): Double {
        require(args.size == 1) { "abs requires 1 argument" }
        return abs(toDouble(args[0]))
    }

    private fun nativeFloor(args: List<Any?>): Double {
        require(args.size == 1) { "floor requires 1 argument" }
        return floor(toDouble(args[0]))
    }

    private fun nativeCeil(args: List<Any?>): Double {
        require(args.size == 1) { "ceil requires 1 argument" }
        return ceil(toDouble(args[0]))
    }

    private fun nativeRound(args: List<Any?>): Double {
        require(args.size == 1) { "round requires 1 argument" }
        return round(toDouble(args[0]))
    }

    private fun nativeMin(args: List<Any?>): Double {
        require(args.size >= 2) { "min requires at least 2 arguments" }
        return args.minOf { toDouble(it) }
    }

    private fun nativeMax(args: List<Any?>): Double {
        require(args.size >= 2) { "max requires at least 2 arguments" }
        return args.maxOf { toDouble(it) }
    }

    private fun nativePow(args: List<Any?>): Double {
        require(args.size == 2) { "pow requires 2 arguments" }
        return toDouble(args[0]).pow(toDouble(args[1]))
    }

    private fun nativeSqrt(args: List<Any?>): Double {
        require(args.size == 1) { "sqrt requires 1 argument" }
        val n = toDouble(args[0])
        require(n >= 0) { "sqrt of negative number" }
        return sqrt(n)
    }

    private fun nativeSin(args: List<Any?>): Double {
        require(args.size == 1) { "sin requires 1 argument" }
        return sin(toDouble(args[0]))
    }

    private fun nativeCos(args: List<Any?>): Double {
        require(args.size == 1) { "cos requires 1 argument" }
        return cos(toDouble(args[0]))
    }

    private fun nativeTan(args: List<Any?>): Double {
        require(args.size == 1) { "tan requires 1 argument" }
        return tan(toDouble(args[0]))
    }

    private fun nativeLog(args: List<Any?>): Double {
        require(args.size == 1) { "log requires 1 argument" }
        val n = toDouble(args[0])
        require(n > 0) { "log of non-positive number" }
        return ln(n)
    }

    private fun nativeLog10(args: List<Any?>): Double {
        require(args.size == 1) { "log10 requires 1 argument" }
        val n = toDouble(args[0])
        require(n > 0) { "log10 of non-positive number" }
        return log10(n)
    }

    private fun nativeExp(args: List<Any?>): Double {
        require(args.size == 1) { "exp requires 1 argument" }
        return exp(toDouble(args[0]))
    }

    // ============ Random functions ============

    private fun nativeRandom(args: List<Any?>): Double {
        require(args.isEmpty()) { "random takes no arguments" }
        return Random.nextDouble()
    }

    private fun nativeRandomInt(args: List<Any?>): Double {
        require(args.size == 2) { "randomInt requires 2 arguments (min, max)" }
        val min = toDouble(args[0]).toInt()
        val max = toDouble(args[1]).toInt()
        require(min < max) { "randomInt: min must be less than max" }
        return Random.nextInt(min, max + 1).toDouble()
    }

    private fun nativeRandomUUID(args: List<Any?>): String {
        require(args.isEmpty()) { "randomUUID takes no arguments" }
        return UUID.randomUUID().toString()
    }

    // ============ Crypto/Hash functions ============

    private fun nativeMd5(args: List<Any?>): String {
        require(args.size == 1) { "md5 requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("md5 requires a string argument")
        return hashString("MD5", str)
    }

    private fun nativeSha1(args: List<Any?>): String {
        require(args.size == 1) { "sha1 requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("sha1 requires a string argument")
        return hashString("SHA-1", str)
    }

    private fun nativeSha256(args: List<Any?>): String {
        require(args.size == 1) { "sha256 requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("sha256 requires a string argument")
        return hashString("SHA-256", str)
    }

    private fun hashString(algorithm: String, input: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ============ Array functions ============

    private fun nativeSort(args: List<Any?>): List<Any?> {
        require(args.size in 1..2) { "sort requires 1 or 2 arguments (array, [order])" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException(
                                "sort requires an array as first argument"
                        )

        val ascending =
                if (args.size == 2) {
                    val order =
                            args[1] as? String
                                    ?: throw IllegalArgumentException(
                                            "sort requires a string as second argument"
                                    )
                    order != "desc"
                } else true

        val result = arr.toMutableList()
        result.sortWith { a, b ->
            val cmp = compareValues(a, b)
            if (ascending) cmp else -cmp
        }
        return result
    }

    private fun nativeSortBy(args: List<Any?>): List<Any?> {
        require(args.size in 2..3) { "sortBy requires 2 or 3 arguments (array, field, [order])" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException(
                                "sortBy requires an array as first argument"
                        )
        val field =
                args[1] as? String
                        ?: throw IllegalArgumentException(
                                "sortBy requires a string field name as second argument"
                        )

        val ascending =
                if (args.size == 3) {
                    val order =
                            args[2] as? String
                                    ?: throw IllegalArgumentException(
                                            "sortBy requires a string as third argument"
                                    )
                    order != "desc"
                } else true

        val result = arr.toMutableList()
        result.sortWith { a, b ->
            val valA = getFieldValue(a, field)
            val valB = getFieldValue(b, field)
            val cmp = compareValues(valA, valB)
            if (ascending) cmp else -cmp
        }
        return result
    }

    private fun nativeReverse(args: List<Any?>): List<Any?> {
        require(args.size == 1) { "reverse requires 1 argument" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException("reverse requires an array argument")
        return arr.reversed()
    }

    private fun nativeSize(args: List<Any?>): Double {
        require(args.size == 1) { "size requires 1 argument" }
        return when (val v = args[0]) {
            is List<*> -> v.size.toDouble()
            is String -> v.length.toDouble()
            is Map<*, *> -> v.size.toDouble()
            else -> throw IllegalArgumentException("size requires an array, string, or object")
        }
    }

    private fun nativeFirst(args: List<Any?>): Any? {
        require(args.size == 1) { "first requires 1 argument" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException("first requires an array argument")
        return arr.firstOrNull()
    }

    private fun nativeLast(args: List<Any?>): Any? {
        require(args.size == 1) { "last requires 1 argument" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException("last requires an array argument")
        return arr.lastOrNull()
    }

    private fun nativeSlice(args: List<Any?>): List<Any?> {
        require(args.size in 2..3) { "slice requires 2 or 3 arguments (array, start, [end])" }
        @Suppress("UNCHECKED_CAST")
        val arr =
                args[0] as? List<Any?>
                        ?: throw IllegalArgumentException(
                                "slice requires an array as first argument"
                        )
        val start = toDouble(args[0]).toInt().let { maxOf(0, it) }

        if (start >= arr.size) return emptyList()

        return if (args.size == 3) {
            val end = toDouble(args[2]).toInt().let { minOf(arr.size, it) }
            if (end < start) emptyList() else arr.subList(start, end)
        } else {
            arr.subList(start, arr.size)
        }
    }

    // ============ Helper functions ============

    private fun toDouble(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun compareValues(a: Any?, b: Any?): Int {
        if (a == null && b == null) return 0
        if (a == null) return -1
        if (b == null) return 1

        val aNum = (a as? Double) ?: (a as? Int)?.toDouble() ?: (a as? Long)?.toDouble()
        val bNum = (b as? Double) ?: (b as? Int)?.toDouble() ?: (b as? Long)?.toDouble()

        if (aNum != null && bNum != null) {
            return aNum.compareTo(bNum)
        }

        return a.toString().compareTo(b.toString())
    }

    @Suppress("UNCHECKED_CAST")
    private fun getFieldValue(obj: Any?, field: String): Any? {
        if (obj is Map<*, *>) {
            return (obj as Map<String, Any?>)[field]
        }
        return null
    }

    private fun parseSimpleJson(str: String): Any? {
        val trimmed = str.trim()
        return when {
            trimmed == "null" -> null
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                    trimmed.substring(1, trimmed.length - 1)
            trimmed.startsWith("[") && trimmed.endsWith("]") -> parseJsonArray(trimmed)
            trimmed.startsWith("{") && trimmed.endsWith("}") -> parseJsonObject(trimmed)
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> throw IllegalArgumentException("invalid JSON: $str")
        }
    }

    private fun parseJsonArray(str: String): List<Any?> {
        val content = str.substring(1, str.length - 1).trim()
        if (content.isEmpty()) return emptyList()
        return content.split(",").map { parseSimpleJson(it.trim()) }
    }

    private fun parseJsonObject(str: String): Map<String, Any?> {
        val content = str.substring(1, str.length - 1).trim()
        if (content.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Any?>()
        for (pair in content.split(",")) {
            val (key, value) =
                    pair.split(":").let {
                        it[0].trim().removeSurrounding("\"") to
                                parseSimpleJson(it.getOrElse(1) { "null" }.trim())
                    }
            result[key] = value
        }
        return result
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
