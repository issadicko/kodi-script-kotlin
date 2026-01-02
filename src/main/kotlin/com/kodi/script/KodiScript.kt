package com.kodi.script

import com.kodi.script.cache.ASTCache
import com.kodi.script.interpreter.Interpreter
import com.kodi.script.lexer.Lexer
import com.kodi.script.natives.NativeFunc
import com.kodi.script.natives.NativeFunctions
import com.kodi.script.parser.Parser

/** Result of script execution. */
data class ScriptResult(
        val value: Any? = null,
        val output: List<String> = emptyList(),
        val errors: List<String> = emptyList()
) {
    val hasErrors: Boolean
        get() = errors.isNotEmpty()
}

/**
 * KodiScript is the main entry point for the KodiScript SDK.
 *
 * Usage:
 * ```kotlin
 * val result = KodiScript.run("""
 *     let name = "Kodi"
 *     print("Hello " + name)
 * """)
 * ```
 */
class KodiScript
private constructor(
        private val source: String,
        private val variables: Map<String, Any?> = emptyMap(),
        private val customFunctions: Map<String, NativeFunc> = emptyMap(),
        private val useCache: Boolean = true,
        private val maxOps: Long = 0
) {

    /** Builder for KodiScript execution. */
    class Builder(private val source: String) {
        private val variables = mutableMapOf<String, Any?>()
        private val customFunctions = mutableMapOf<String, NativeFunc>()
        private var useCache = true
        private var maxOps: Long = 0 // 0 = unlimited

        /** Inject host variables into the script context. */
        fun withVariables(vars: Map<String, Any?>): Builder {
            variables.putAll(vars)
            return this
        }

        /** Inject a single variable. */
        fun withVariable(name: String, value: Any?): Builder {
            variables[name] = value
            return this
        }

        /** Register a custom native function. */
        fun registerFunction(name: String, fn: NativeFunc): Builder {
            customFunctions[name] = fn
            return this
        }

        /** Bind a Kotlin object to the script context with reflective access. */
        fun bind(name: String, obj: Any): Builder {
            variables[name] = obj
            return this
        }

        /** Enable or disable AST caching. */
        fun withCache(enabled: Boolean): Builder {
            useCache = enabled
            return this
        }

        /** Set the maximum number of operations allowed. */
        fun withMaxOperations(maxOps: Long): Builder {
            this.maxOps = maxOps
            return this
        }

        /** Execute the script. */
        fun execute(): ScriptResult {
            return KodiScript(source, variables, customFunctions, useCache, maxOps).execute()
        }
    }

    /** Execute the script and return the result. */
    fun execute(): ScriptResult {
        // Try to get from cache first
        val program =
                if (useCache) {
                    ASTCache.default.get(source)
                } else {
                    null
                }
                        ?: run {
                            // Parse if not cached
                            val lexer = Lexer(source)
                            val parser = Parser(lexer)
                            val parsedProgram = parser.parseProgram()

                            if (parser.errors().isNotEmpty()) {
                                return ScriptResult(errors = parser.errors())
                            }

                            // Store in cache
                            if (useCache) {
                                ASTCache.default.set(source, parsedProgram)
                            }

                            parsedProgram
                        }

        // Use singleton for built-ins, create copy only if custom functions are registered
        val natives =
                if (customFunctions.isEmpty()) {
                    NativeFunctions.shared
                } else {
                    NativeFunctions.withBuiltins().apply {
                        customFunctions.forEach { (name, fn) -> register(name, fn) }
                    }
                }

        // Interpreter with environment and natives
        val interpreter =
                if (variables.isNotEmpty()) {
                    val env = com.kodi.script.interpreter.Environment()
                    variables.forEach { (k, v) -> env.set(k, v) }
                    Interpreter(env, natives)
                } else {
                    Interpreter(natives = natives)
                }

        // Apply operation limit if set
        if (maxOps > 0) {
            interpreter.setMaxOperations(maxOps)
        }

        return try {
            val value = interpreter.eval(program)
            ScriptResult(value = value, output = interpreter.getOutput())
        } catch (e: Exception) {
            ScriptResult(errors = listOf(e.message ?: "Unknown error"))
        }
    }

    companion object {
        /** Create a builder for script execution. */
        fun builder(source: String): Builder = Builder(source)

        /** Run a script with optional variables. */
        fun run(source: String, variables: Map<String, Any?> = emptyMap()): ScriptResult {
            return Builder(source).withVariables(variables).execute()
        }

        /** Simple evaluation function. */
        fun eval(source: String): Any? {
            val result = run(source)
            if (result.hasErrors) {
                throw KodiScriptException(result.errors)
            }
            return result.value
        }
    }
}

/** Exception thrown when script execution fails. */
class KodiScriptException(val errors: List<String>) :
        RuntimeException(errors.firstOrNull() ?: "Unknown error")
