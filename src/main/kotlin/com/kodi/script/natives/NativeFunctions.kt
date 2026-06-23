package com.kodi.script.natives

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

typealias NativeFunc = (List<Any?>) -> Any?

/** Registry of native functions for KodiScript. */
class NativeFunctions private constructor(private val fallback: NativeFunctions? = null) {
    private val functions = mutableMapOf<String, NativeFunc>()

    companion object {
        /** Shared singleton instance for built-in functions. */
        val shared: NativeFunctions by lazy { NativeFunctions().apply { registerBuiltins() } }

        /**
         * Create a registry for per-script custom functions that falls back to the
         * shared builtins. Layered lookup avoids copying the ~80 builtins on every
         * execution that registers custom functions.
         */
        fun withBuiltins(): NativeFunctions = NativeFunctions(fallback = shared)
    }

    /** Custom functions take priority; unknown names fall back to the builtins. */
    fun get(name: String): NativeFunc? = functions[name] ?: fallback?.get(name)

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
        functions["trim"] = ::nativeTrim
        functions["split"] = ::nativeSplit
        functions["join"] = ::nativeJoin
        functions["replace"] = ::nativeReplace
        functions["contains"] = ::nativeContains
        functions["startsWith"] = ::nativeStartsWith
        functions["endsWith"] = ::nativeEndsWith
        functions["indexOf"] = ::nativeIndexOf
        functions["padLeft"] = ::nativePadLeft
        functions["padRight"] = ::nativePadRight
        functions["repeat"] = ::nativeRepeat

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
        functions["range"] = ::nativeRange
        functions["sum"] = ::nativeSum
        functions["avg"] = ::nativeAvg
        functions["unique"] = ::nativeUnique
        functions["flatten"] = ::nativeFlatten
        functions["push"] = ::nativePush
        functions["concat"] = ::nativeConcat

        // Object functions
        functions["keys"] = ::nativeKeys
        functions["values"] = ::nativeValues
        functions["entries"] = ::nativeEntries
        functions["has"] = ::nativeHas

        // Number parsing
        functions["parseInt"] = ::nativeParseInt
        functions["parseFloat"] = ::nativeParseFloat

        // Regex
        functions["regexMatch"] = ::nativeRegexMatch
        functions["regexReplace"] = ::nativeRegexReplace

        // Date/Time functions
        functions["now"] = ::nativeNow
        functions["date"] = ::nativeDate
        functions["time"] = ::nativeTime
        functions["datetime"] = ::nativeDatetime
        functions["timestamp"] = ::nativeTimestamp
        functions["formatDate"] = ::nativeFormatDate
        functions["year"] = ::nativeYear
        functions["month"] = ::nativeMonth
        functions["day"] = ::nativeDay
        functions["hour"] = ::nativeHour
        functions["minute"] = ::nativeMinute
        functions["second"] = ::nativeSecond
        functions["dayOfWeek"] = ::nativeDayOfWeek
        functions["addDays"] = ::nativeAddDays
        functions["addHours"] = ::nativeAddHours
        functions["diffDays"] = ::nativeDiffDays
    }

    // ============ String functions ============

    private fun nativeToString(args: List<Any?>): String {
        require(args.size == 1) { "toString requires 1 argument" }
        return kodiStringify(args[0])
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
        return arr.joinToString(sep) { kodiStringify(it) }
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

    private fun nativePadLeft(args: List<Any?>): String {
        require(args.size >= 2) { "padLeft requires at least 2 arguments" }
        val str = kodiStringify(args[0])
        val length = (args[1] as? Number)?.toInt() ?: 0
        val padChar = if (args.size > 2) (args[2]?.toString()?.firstOrNull() ?: ' ') else ' '
        return str.padStart(length, padChar)
    }

    private fun nativePadRight(args: List<Any?>): String {
        require(args.size >= 2) { "padRight requires at least 2 arguments" }
        val str = kodiStringify(args[0])
        val length = (args[1] as? Number)?.toInt() ?: 0
        val padChar = if (args.size > 2) (args[2]?.toString()?.firstOrNull() ?: ' ') else ' '
        return str.padEnd(length, padChar)
    }

    private fun nativeRepeat(args: List<Any?>): String {
        require(args.size >= 2) { "repeat requires 2 arguments" }
        val str = kodiStringify(args[0])
        val count = (args[1] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        return str.repeat(count)
    }

    // ============ JSON functions ============

    private fun nativeJsonParse(args: List<Any?>): Any? {
        require(args.size == 1) { "jsonParse requires 1 argument" }
        val str =
                args[0] as? String
                        ?: throw IllegalArgumentException("jsonParse requires a string argument")
        val parser = JsonParser(str)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) {
            throw IllegalArgumentException("invalid JSON: unexpected trailing characters")
        }
        return value
    }

    private fun nativeJsonStringify(args: List<Any?>): String {
        require(args.size == 1) { "jsonStringify requires 1 argument" }
        val sb = StringBuilder()
        stringifyValue(args[0], sb)
        return sb.toString()
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
        val start = toDouble(args[1]).toInt().let { maxOf(0, it) }

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

    // ============ More array functions ============

    private fun nativeRange(args: List<Any?>): List<Any?> {
        val start: Int
        val end: Int
        when (args.size) {
            1 -> {
                start = 0
                end = toDouble(args[0]).toInt()
            }
            2 -> {
                start = toDouble(args[0]).toInt()
                end = toDouble(args[1]).toInt()
            }
            else -> throw IllegalArgumentException("range requires 1 or 2 arguments")
        }
        if (end <= start) return emptyList()
        return (start until end).map { it.toDouble() }
    }

    private fun nativeSum(args: List<Any?>): Double {
        require(args.size == 1) { "sum requires 1 argument" }
        val arr =
                args[0] as? List<*>
                        ?: throw IllegalArgumentException("sum requires an array argument")
        return arr.sumOf { toDouble(it) }
    }

    private fun nativeAvg(args: List<Any?>): Double {
        require(args.size == 1) { "avg requires 1 argument" }
        val arr =
                args[0] as? List<*>
                        ?: throw IllegalArgumentException("avg requires an array argument")
        if (arr.isEmpty()) return 0.0
        return arr.sumOf { toDouble(it) } / arr.size
    }

    private fun nativeUnique(args: List<Any?>): List<Any?> {
        require(args.size == 1) { "unique requires 1 argument" }
        val arr =
                args[0] as? List<*>
                        ?: throw IllegalArgumentException("unique requires an array argument")
        return arr.distinct()
    }

    private fun nativeFlatten(args: List<Any?>): List<Any?> {
        require(args.size == 1) { "flatten requires 1 argument" }
        val arr =
                args[0] as? List<*>
                        ?: throw IllegalArgumentException("flatten requires an array argument")
        val result = mutableListOf<Any?>()
        for (v in arr) {
            if (v is List<*>) result.addAll(v) else result.add(v)
        }
        return result
    }

    private fun nativePush(args: List<Any?>): List<Any?> {
        require(args.size >= 2) { "push requires at least 2 arguments (array, item...)" }
        val arr =
                args[0] as? List<*>
                        ?: throw IllegalArgumentException("push requires an array as first argument")
        return arr + args.subList(1, args.size)
    }

    private fun nativeConcat(args: List<Any?>): List<Any?> {
        val result = mutableListOf<Any?>()
        for (a in args) {
            val arr =
                    a as? List<*>
                            ?: throw IllegalArgumentException("concat requires array arguments")
            result.addAll(arr)
        }
        return result
    }

    // ============ Object functions ============

    @Suppress("UNCHECKED_CAST")
    private fun asObject(value: Any?, fn: String): Map<String, Any?> =
            value as? Map<String, Any?>
                    ?: throw IllegalArgumentException("$fn requires an object argument")

    private fun nativeKeys(args: List<Any?>): List<Any?> {
        require(args.size == 1) { "keys requires 1 argument" }
        return asObject(args[0], "keys").keys.sorted()
    }

    private fun nativeValues(args: List<Any?>): List<Any?> {
        require(args.size == 1) { "values requires 1 argument" }
        val m = asObject(args[0], "values")
        return m.keys.sorted().map { m[it] }
    }

    private fun nativeEntries(args: List<Any?>): List<Any?> {
        require(args.size == 1) { "entries requires 1 argument" }
        val m = asObject(args[0], "entries")
        return m.keys.sorted().map { listOf<Any?>(it, m[it]) }
    }

    private fun nativeHas(args: List<Any?>): Boolean {
        require(args.size == 2) { "has requires 2 arguments" }
        return when (val coll = args[0]) {
            is Map<*, *> -> coll.containsKey(args[1])
            is List<*> -> coll.contains(args[1])
            else ->
                    throw IllegalArgumentException(
                            "has requires an object or array as first argument"
                    )
        }
    }

    // ============ Number parsing ============

    private fun nativeParseInt(args: List<Any?>): Double {
        require(args.size == 1) { "parseInt requires 1 argument" }
        return when (val v = args[0]) {
            is Double -> truncate(v)
            is Int -> v.toDouble()
            is Long -> v.toDouble()
            is String ->
                    truncate(
                            v.trim().toDoubleOrNull()
                                    ?: throw IllegalArgumentException(
                                            "cannot parse '$v' as integer"
                                    )
                    )
            else -> throw IllegalArgumentException("parseInt requires a string or number")
        }
    }

    private fun nativeParseFloat(args: List<Any?>): Double {
        require(args.size == 1) { "parseFloat requires 1 argument" }
        return when (val v = args[0]) {
            is Double -> v
            is Int -> v.toDouble()
            is Long -> v.toDouble()
            is String ->
                    v.trim().toDoubleOrNull()
                            ?: throw IllegalArgumentException("cannot parse '$v' as number")
            else -> throw IllegalArgumentException("parseFloat requires a string or number")
        }
    }

    // ============ Regex ============

    private fun nativeRegexMatch(args: List<Any?>): Boolean {
        require(args.size == 2) { "regexMatch requires 2 arguments (string, pattern)" }
        val s =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "regexMatch requires a string as first argument"
                        )
        val pat =
                args[1] as? String
                        ?: throw IllegalArgumentException("regexMatch requires a string pattern")
        return Regex(pat).containsMatchIn(s)
    }

    private fun nativeRegexReplace(args: List<Any?>): String {
        require(args.size == 3) {
            "regexReplace requires 3 arguments (string, pattern, replacement)"
        }
        val s =
                args[0] as? String
                        ?: throw IllegalArgumentException(
                                "regexReplace requires a string as first argument"
                        )
        val pat =
                args[1] as? String
                        ?: throw IllegalArgumentException("regexReplace requires a string pattern")
        val repl =
                args[2] as? String
                        ?: throw IllegalArgumentException(
                                "regexReplace requires a string replacement"
                        )
        return Regex(pat).replace(s, repl)
    }

    /**
     * Minimal but correct recursive-descent JSON parser. Handles nested objects
     * and arrays, escaped strings (including commas/colons inside strings),
     * unicode escapes, and the full JSON number grammar. Produces KodiScript
     * values: Double, String, Boolean, null, List, and (linked) Map.
     */
    private class JsonParser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("invalid JSON: unexpected end of input")
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't',
                'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun expect(c: Char) {
            if (atEnd() || s[i] != c) {
                throw IllegalArgumentException("invalid JSON: expected '$c' at position $i")
            }
            i++
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && s[i] == '}') {
                i++
                return result
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[i] != '"') {
                    throw IllegalArgumentException("invalid JSON: expected string key")
                }
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (atEnd()) throw IllegalArgumentException("invalid JSON: unterminated object")
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return result
                    }
                    else -> throw IllegalArgumentException("invalid JSON: expected ',' or '}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && s[i] == ']') {
                i++
                return result
            }
            while (true) {
                result.add(parseValue())
                skipWhitespace()
                if (atEnd()) throw IllegalArgumentException("invalid JSON: unterminated array")
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return result
                    }
                    else -> throw IllegalArgumentException("invalid JSON: expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (!atEnd() && s[i] != '"') {
                val c = s[i]
                if (c == '\\') {
                    i++
                    if (atEnd()) break
                    when (s[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 4 >= s.length) {
                                throw IllegalArgumentException("invalid JSON: bad unicode escape")
                            }
                            val hex = s.substring(i + 1, i + 5)
                            sb.append(
                                    hex.toIntOrNull(16)?.toChar()
                                            ?: throw IllegalArgumentException(
                                                    "invalid JSON: bad unicode escape"
                                            )
                            )
                            i += 4
                        }
                        else -> sb.append(s[i])
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            expect('"')
            return sb.toString()
        }

        private fun parseBoolean(): Boolean =
                when {
                    s.startsWith("true", i) -> {
                        i += 4
                        true
                    }
                    s.startsWith("false", i) -> {
                        i += 5
                        false
                    }
                    else -> throw IllegalArgumentException("invalid JSON: expected boolean")
                }

        private fun parseNull(): Any? {
            if (s.startsWith("null", i)) {
                i += 4
                return null
            }
            throw IllegalArgumentException("invalid JSON: expected null")
        }

        private fun parseNumber(): Double {
            val start = i
            if (!atEnd() && s[i] == '-') i++
            while (!atEnd() &&
                    (s[i].isDigit() ||
                            s[i] == '.' ||
                            s[i] == 'e' ||
                            s[i] == 'E' ||
                            s[i] == '+' ||
                            s[i] == '-')) {
                i++
            }
            val numStr = s.substring(start, i)
            return numStr.toDoubleOrNull()
                    ?: throw IllegalArgumentException("invalid JSON: invalid number '$numStr'")
        }
    }

    private fun stringifyValue(value: Any?, sb: StringBuilder) {
        when (value) {
            null -> sb.append("null")
            is String -> appendJsonString(value, sb)
            is Boolean -> sb.append(value.toString())
            is Double ->
                    // Render integral doubles without a trailing ".0" (matches Go and
                    // the interpreter's own number formatting).
                    if (!value.isInfinite() && !value.isNaN() && value == value.toLong().toDouble()) {
                        sb.append(value.toLong().toString())
                    } else {
                        sb.append(value.toString())
                    }
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    appendJsonString(k.toString(), sb)
                    sb.append(':')
                    stringifyValue(v, sb)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                var first = true
                for (item in value) {
                    if (!first) sb.append(',')
                    first = false
                    stringifyValue(item, sb)
                }
                sb.append(']')
            }
            else -> appendJsonString(value.toString(), sb)
        }
    }

    /** Appends a properly escaped JSON string literal (with surrounding quotes). */
    private fun appendJsonString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\t' -> sb.append("\\t")
                '\r' -> sb.append("\\r")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    // ============ Date/Time functions ============

    private fun nativeNow(args: List<Any?>): Double {
        require(args.isEmpty()) { "now takes no arguments" }
        return System.currentTimeMillis().toDouble()
    }

    private fun nativeDate(args: List<Any?>): String {
        require(args.isEmpty()) { "date takes no arguments" }
        return SimpleDateFormat("yyyy-MM-dd").format(Date())
    }

    private fun nativeTime(args: List<Any?>): String {
        require(args.isEmpty()) { "time takes no arguments" }
        return SimpleDateFormat("HH:mm:ss").format(Date())
    }

    private fun nativeDatetime(args: List<Any?>): String {
        require(args.isEmpty()) { "datetime takes no arguments" }
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(Date())
    }

    private fun nativeTimestamp(args: List<Any?>): Double {
        if (args.isEmpty()) return System.currentTimeMillis().toDouble()
        val dateStr =
                args[0] as? String
                        ?: throw IllegalArgumentException("timestamp requires a string argument")
        val formats =
                listOf(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                        "yyyy-MM-dd'T'HH:mm:ssXXX",
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyy-MM-dd"
                )
        for (format in formats) {
            try {
                return SimpleDateFormat(format).parse(dateStr)?.time?.toDouble()
                        ?: throw IllegalArgumentException("cannot parse date")
            } catch (_: Exception) {}
        }
        throw IllegalArgumentException("cannot parse date: $dateStr")
    }

    private fun nativeFormatDate(args: List<Any?>): String {
        require(args.size in 1..2) { "formatDate requires 1 or 2 arguments" }
        val ts = toDouble(args[0]).toLong()
        val format =
                if (args.size == 2) {
                    args[1] as? String
                            ?: throw IllegalArgumentException(
                                    "formatDate requires a string as second argument"
                            )
                } else "YYYY-MM-DD"

        val date = Date(ts)
        var result = format
        val cal = java.util.Calendar.getInstance().apply { time = date }
        result = result.replace("YYYY", "%04d".format(cal.get(java.util.Calendar.YEAR)))
        result = result.replace("MM", "%02d".format(cal.get(java.util.Calendar.MONTH) + 1))
        result = result.replace("DD", "%02d".format(cal.get(java.util.Calendar.DAY_OF_MONTH)))
        result = result.replace("HH", "%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY)))
        result = result.replace("mm", "%02d".format(cal.get(java.util.Calendar.MINUTE)))
        result = result.replace("ss", "%02d".format(cal.get(java.util.Calendar.SECOND)))
        return result
    }

    private fun nativeYear(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        return cal.get(java.util.Calendar.YEAR).toDouble()
    }

    private fun nativeMonth(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        return (cal.get(java.util.Calendar.MONTH) + 1).toDouble()
    }

    private fun nativeDay(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        return cal.get(java.util.Calendar.DAY_OF_MONTH).toDouble()
    }

    private fun nativeHour(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        return cal.get(java.util.Calendar.HOUR_OF_DAY).toDouble()
    }

    private fun nativeMinute(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        return cal.get(java.util.Calendar.MINUTE).toDouble()
    }

    private fun nativeSecond(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        return cal.get(java.util.Calendar.SECOND).toDouble()
    }

    private fun nativeDayOfWeek(args: List<Any?>): Double {
        val ts = if (args.isEmpty()) System.currentTimeMillis() else toDouble(args[0]).toLong()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        // Calendar.SUNDAY = 1, we want 0 = Sunday
        return (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1).toDouble()
    }

    private fun nativeAddDays(args: List<Any?>): Double {
        require(args.size == 2) { "addDays requires 2 arguments (timestamp, days)" }
        val ts = toDouble(args[0]).toLong()
        val days = toDouble(args[1]).toInt()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        cal.add(java.util.Calendar.DAY_OF_MONTH, days)
        return cal.timeInMillis.toDouble()
    }

    private fun nativeAddHours(args: List<Any?>): Double {
        require(args.size == 2) { "addHours requires 2 arguments (timestamp, hours)" }
        val ts = toDouble(args[0]).toLong()
        val hours = toDouble(args[1]).toInt()
        val cal = java.util.Calendar.getInstance().apply { time = Date(ts) }
        cal.add(java.util.Calendar.HOUR_OF_DAY, hours)
        return cal.timeInMillis.toDouble()
    }

    private fun nativeDiffDays(args: List<Any?>): Double {
        require(args.size == 2) { "diffDays requires 2 arguments (timestamp1, timestamp2)" }
        val ts1 = toDouble(args[0]).toLong()
        val ts2 = toDouble(args[1]).toLong()
        val diffMs = ts2 - ts1
        return (diffMs / (1000 * 60 * 60 * 24)).toDouble()
    }
}
