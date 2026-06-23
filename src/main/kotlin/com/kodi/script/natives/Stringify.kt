package com.kodi.script.natives

/**
 * Renders a KodiScript value in canonical form. Shared across the interpreter
 * (print, string templates, string concatenation) and natives (toString, join)
 * so that output is identical across language implementations:
 * - integral numbers print without a trailing ".0" (3, not 3.0)
 * - arrays print as "[1, 2, 3]"
 * - objects print as "{a: 1, b: 2}" with keys sorted for determinism
 * - strings are not quoted (use jsonStringify for quoted output)
 */
fun kodiStringify(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> value
            is Boolean -> value.toString()
            is Double ->
                    if (!value.isInfinite() && !value.isNaN() && value == value.toLong().toDouble())
                            value.toLong().toString()
                    else value.toString()
            is Int -> value.toString()
            is Long -> value.toString()
            is List<*> -> value.joinToString(", ", "[", "]") { kodiStringify(it) }
            is Map<*, *> ->
                    value.entries.sortedBy { it.key.toString() }.joinToString(", ", "{", "}") {
                        "${it.key}: ${kodiStringify(it.value)}"
                    }
            else -> value.toString()
        }
