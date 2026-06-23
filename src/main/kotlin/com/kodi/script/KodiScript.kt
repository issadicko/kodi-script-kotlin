package com.kodi.script

import com.kodi.script.cache.ASTCache
import com.kodi.script.interpreter.Interpreter
import com.kodi.script.interpreter.MaxOperationsExceeded
import com.kodi.script.interpreter.TimeoutException
import com.kodi.script.lexer.Lexer
import com.kodi.script.natives.NativeFunc
import com.kodi.script.natives.NativeFunctions
import com.kodi.script.parser.Parser

/** Classifies why execution failed, for programmatic handling. */
enum class ErrorKind {
    NONE,
    PARSE,
    RUNTIME,
    TIMEOUT,
    MAX_OPERATIONS
}

/** Result of script execution. */
data class ScriptResult(
        val value: Any? = null,
        val output: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val errorKind: ErrorKind = ErrorKind.NONE
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
        private val maxOps: Long = 0,
        private val timeoutMs: Long = 0,
        private val silent: Boolean = false,
        private val outputSink: ((String) -> Unit)? = null
) {

    /** Builder for KodiScript execution. */
    class Builder(private val source: String) {
        private val variables = mutableMapOf<String, Any?>()
        private val customFunctions = mutableMapOf<String, NativeFunc>()
        private var useCache = true
        private var maxOps: Long = 0 // 0 = unlimited
        private var timeoutMs: Long = 0 // 0 = no timeout
        private var silent = false // suppress stdout from print()
        private var outputSink: ((String) -> Unit)? = null // route print() to a callback

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

        /** Set the execution timeout in milliseconds. */
        fun withTimeout(timeoutMs: Long): Builder {
            this.timeoutMs = timeoutMs
            return this
        }

        /** Suppress stdout from print(); output is still captured in the result. */
        fun withSilentPrint(silent: Boolean): Builder {
            this.silent = silent
            return this
        }

        /** Route print() output to [sink] instead of stdout. Output is still captured. */
        fun withOutput(sink: (String) -> Unit): Builder {
            this.outputSink = sink
            return this
        }

        /** Execute the script. */
        fun execute(): ScriptResult {
            return KodiScript(
                            source,
                            variables,
                            customFunctions,
                            useCache,
                            maxOps,
                            timeoutMs,
                            silent,
                            outputSink
                    )
                    .execute()
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
                                return ScriptResult(
                                        errors = parser.errors(),
                                        errorKind = ErrorKind.PARSE
                                )
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

        // Apply timeout if set
        if (timeoutMs > 0) {
            interpreter.setDeadline(System.currentTimeMillis() + timeoutMs)
        }

        // Apply silent print setting (output is still captured in the result)
        interpreter.setSilent(silent)

        // Route print() to a custom sink if provided
        outputSink?.let { interpreter.setOutputSink(it) }

        return try {
            val value = interpreter.eval(program)
            ScriptResult(value = value, output = interpreter.getOutput())
        } catch (e: MaxOperationsExceeded) {
            ScriptResult(
                    errors = listOf(e.message ?: "max operations exceeded"),
                    errorKind = ErrorKind.MAX_OPERATIONS
            )
        } catch (e: TimeoutException) {
            ScriptResult(
                    errors = listOf(e.message ?: "execution timeout"),
                    errorKind = ErrorKind.TIMEOUT
            )
        } catch (e: StackOverflowError) {
            // Backstop in case deep recursion exhausts the JVM stack before the
            // interpreter's own call-depth guard fires.
            ScriptResult(
                    errors = listOf("maximum call depth exceeded"),
                    errorKind = ErrorKind.RUNTIME
            )
        } catch (e: Exception) {
            ScriptResult(
                    errors = listOf(e.message ?: "Unknown error"),
                    errorKind = ErrorKind.RUNTIME
            )
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
