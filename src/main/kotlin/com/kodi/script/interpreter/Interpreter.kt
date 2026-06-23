package com.kodi.script.interpreter

import com.kodi.script.ast.*
import com.kodi.script.natives.NativeFunctions
import com.kodi.script.natives.kodiStringify
import com.kodi.script.token.Token
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/** Environment holds variable bindings. */
class Environment(private val outer: Environment? = null) {
    private val store = mutableMapOf<String, Any?>()

    /** Sentinel returned by [get] when a name is unbound (distinct from a null value). */
    object NotFound

    /** Returns the bound value, or [NotFound] if the name is not bound. */
    fun get(name: String): Any? {
        val value = store[name]
        if (value != null || store.containsKey(name)) return value
        return if (outer != null) outer.get(name) else NotFound
    }

    fun set(name: String, value: Any?) {
        store[name] = value
    }
}

/** Wrapper to signal early return from evaluation. */
class ReturnValue(val value: Any?)

/** Signal to break out of the nearest enclosing loop. */
object BreakSignal

/** Signal to continue to the next iteration of the nearest loop. */
object ContinueSignal

/** Function value (user defined). */
data class FunctionValue(
        val parameters: List<Identifier>,
        val body: BlockStatement,
        val env: Environment
)

/** Native function wrapper. */
data class NativeFunctionValue(val fn: (List<Any?>) -> Any?)

/** Exception thrown when max operations is exceeded. */
class MaxOperationsExceeded : RuntimeException("max operations exceeded")

/** Exception thrown when execution timeout is exceeded. */
class TimeoutException : RuntimeException("execution timeout")

/** Runtime error carrying source position information. */
class KodiRuntimeException(message: String, val line: Int = 0, val col: Int = 0) :
        RuntimeException(if (line > 0) "line $line, col $col: $message" else message)

/** Exception thrown when the function call-depth limit is exceeded. */
class MaxCallDepthExceeded : RuntimeException("maximum call depth exceeded")

/** Cached resolution of a reflective member access (method, property, or absent). */
private sealed class ReflectMember {
    class Method(val fn: KCallable<*>) : ReflectMember()
    class Property(val prop: KCallable<*>) : ReflectMember()
    object None : ReflectMember()
}

/** Interpreter evaluates AST nodes. */
class Interpreter(
        private var env: Environment = Environment(),
        private val natives: NativeFunctions = NativeFunctions.shared
) {
    private var opCount: Long = 0
    private var maxOps: Long = 0 // 0 = unlimited
    private var deadline: Long = 0 // 0 = no timeout
    private val output = mutableListOf<String>()
    private var silent = false // when true, print() does not write to stdout
    private var outputSink: ((String) -> Unit)? = null // when set, print() routes here
    private var callDepth = 0 // current user-function call depth (recursion guard)

    companion object {
        /** Maximum nested user-function call depth (recursion guard). */
        const val MAX_CALL_DEPTH = 1000

        /** Caches reflective member resolution per (class, name) — Kotlin reflection is costly. */
        private val reflectCache =
                java.util.concurrent.ConcurrentHashMap<Pair<KClass<*>, String>, ReflectMember>()

        fun withVariables(variables: Map<String, Any?>): Interpreter {
            val env = Environment()
            variables.forEach { (k, v) -> env.set(k, v) }
            return Interpreter(env)
        }
    }

    /** Sets the maximum number of operations allowed. */
    fun setMaxOperations(maxOps: Long) {
        this.maxOps = maxOps
        this.opCount = 0
    }

    /** Sets the execution deadline (timestamp in ms). */
    fun setDeadline(deadline: Long) {
        this.deadline = deadline
    }

    /** Checks the operation limit and throws if exceeded. */
    private fun checkOperationLimit() {
        if (maxOps > 0) {
            opCount++
            if (opCount > maxOps) {
                throw MaxOperationsExceeded()
            }
        }
    }

    /** Checks if the deadline has been exceeded. */
    private fun checkDeadline() {
        if (deadline > 0 && System.currentTimeMillis() > deadline) {
            throw TimeoutException()
        }
    }

    fun eval(program: Program): Any? {
        var result: Any? = null
        for (stmt in program.statements) {
            val r = evalStatement(stmt)
            // Unwrap return values at top level
            if (r is ReturnValue) {
                return r.value
            }
            // Ignore a stray break/continue used outside any loop
            if (r === BreakSignal || r === ContinueSignal) {
                continue
            }
            result = r
        }
        return result
    }

    fun getOutput(): List<String> = output

    /** Controls whether print() writes to stdout. Output is always captured. */
    fun setSilent(silent: Boolean) {
        this.silent = silent
    }

    /** Routes print() output to [sink] instead of stdout. Output is still captured. */
    fun setOutputSink(sink: (String) -> Unit) {
        this.outputSink = sink
    }

    private fun evalStatement(stmt: Statement): Any? {
        // Check operation limit at each statement
        checkOperationLimit()
        // Check deadline at each statement
        checkDeadline()

        return try {
            evalStatementBody(stmt)
        } catch (e: MaxOperationsExceeded) {
            throw e
        } catch (e: TimeoutException) {
            throw e
        } catch (e: KodiRuntimeException) {
            throw e // already positioned (innermost statement wins)
        } catch (e: RuntimeException) {
            val tok = stmtToken(stmt)
            throw KodiRuntimeException(e.message ?: "runtime error", tok.line, tok.column)
        }
    }

    private fun stmtToken(stmt: Statement): Token =
            when (stmt) {
                is VarDecl -> stmt.token
                is Assignment -> stmt.token
                is ArrayDestructure -> stmt.token
                is ObjectDestructure -> stmt.token
                is ExpressionStatement -> stmt.token
                is IfStatement -> stmt.token
                is BlockStatement -> stmt.token
                is ReturnStatement -> stmt.token
                is ForStatement -> stmt.token
                is WhileStatement -> stmt.token
                is TryStatement -> stmt.token
                is BreakStatement -> stmt.token
                is ContinueStatement -> stmt.token
            }

    private fun evalTryStatement(stmt: TryStatement): Any? {
        return try {
            evalBlockStatement(stmt.body)
        } catch (e: MaxOperationsExceeded) {
            throw e // not catchable from script
        } catch (e: TimeoutException) {
            throw e // not catchable from script
        } catch (e: RuntimeException) {
            stmt.catchVar?.let { env.set(it.value, e.message ?: "runtime error") }
            evalBlockStatement(stmt.catch)
        }
    }

    private fun evalStatementBody(stmt: Statement): Any? {
        return when (stmt) {
            is VarDecl -> {
                val value = evalExpression(stmt.value)
                env.set(stmt.name.value, value)
                value
            }
            is Assignment -> {
                val value = evalExpression(stmt.value)
                env.set(stmt.name.value, value)
                value
            }
            is ArrayDestructure -> {
                val value = evalExpression(stmt.value)
                val arr =
                        value as? List<*>
                                ?: throw RuntimeException("cannot destructure non-array value")
                stmt.names.forEachIndexed { idx, name ->
                    env.set(name.value, if (idx < arr.size) arr[idx] else null)
                }
                value
            }
            is ObjectDestructure -> {
                val value = evalExpression(stmt.value)
                @Suppress("UNCHECKED_CAST")
                val m =
                        value as? Map<String, Any?>
                                ?: throw RuntimeException("cannot destructure non-object value")
                stmt.names.forEach { name -> env.set(name.value, m[name.value]) }
                value
            }
            is ExpressionStatement -> evalExpression(stmt.expression)
            is IfStatement -> evalIfStatement(stmt)
            is BlockStatement -> evalBlockStatement(stmt)
            is ReturnStatement -> {
                val value = stmt.value?.let { evalExpression(it) }
                ReturnValue(value)
            }
            is ForStatement -> evalForStatement(stmt)
            is WhileStatement -> evalWhileStatement(stmt)
            is TryStatement -> evalTryStatement(stmt)
            is BreakStatement -> BreakSignal
            is ContinueStatement -> ContinueSignal
        }
    }

    private fun evalIfStatement(stmt: IfStatement): Any? {
        val condition = evalExpression(stmt.condition)
        return if (isTruthy(condition)) {
            evalBlockStatement(stmt.consequence)
        } else {
            stmt.alternative?.let { evalBlockStatement(it) }
        }
    }

    private fun evalForStatement(stmt: ForStatement): Any? {
        // Evaluate the iterable
        val iterableVal = evalExpression(stmt.iterable)

        // Must be a List
        val arr =
                iterableVal as? List<*>
                        ?: throw RuntimeException(
                                "for-in requires an array, got ${iterableVal?.javaClass?.simpleName}"
                        )

        var result: Any? = null
        val varName = stmt.variable.value

        for (item in arr) {
            // Check operation limit at each iteration
            checkOperationLimit()
            // Check deadline at each iteration
            checkDeadline()

            // Set loop variable
            env.set(varName, item)

            // Execute body
            val value = evalBlockStatement(stmt.body)

            // Handle return/break/continue signals
            when {
                value is ReturnValue -> return value
                value === BreakSignal -> break
                value === ContinueSignal -> continue
                else -> result = value
            }
        }

        return result
    }

    private fun evalWhileStatement(stmt: WhileStatement): Any? {
        var result: Any? = null

        while (true) {
            // Check operation limit at each iteration
            checkOperationLimit()
            // Check timeout at each iteration
            checkDeadline()

            // Evaluate condition
            val conditionValue = evalExpression(stmt.condition)

            // Exit if condition is false
            if (!isTruthy(conditionValue)) {
                break
            }

            // Execute body
            val value = evalBlockStatement(stmt.body)

            // Handle return/break/continue signals
            when {
                value is ReturnValue -> return value
                value === BreakSignal -> break
                value === ContinueSignal -> continue
                else -> result = value
            }
        }

        return result
    }

    private fun evalBlockStatement(block: BlockStatement): Any? {
        var result: Any? = null
        for (stmt in block.statements) {
            result = evalStatement(stmt)
            // Propagate return/break/continue signals up to the nearest handler
            if (result is ReturnValue || result === BreakSignal || result === ContinueSignal) {
                return result
            }
        }
        return result
    }

    private fun evalStringTemplate(tmpl: StringTemplate): Any {
        val result = StringBuilder()
        for (part in tmpl.parts) {
            result.append(kodiStringify(evalExpression(part)))
        }
        return result.toString()
    }

    private fun evalExpression(expr: Expression): Any? {
        return when (expr) {
            is NumberLiteral -> expr.value
            is StringLiteral -> expr.value
            is StringTemplate -> evalStringTemplate(expr)
            is BooleanLiteral -> expr.value
            is NullLiteral -> null
            is Identifier -> {
                val value = env.get(expr.value)
                if (value !== Environment.NotFound) return value

                val native = natives.get(expr.value)
                if (native != null) return NativeFunctionValue(native)

                throw RuntimeException("undefined variable: ${expr.value}")
            }
            is FunctionLiteral -> FunctionValue(expr.parameters, expr.body, env)
            is BinaryExpr -> evalBinaryExpr(expr)
            is UnaryExpr -> evalUnaryExpr(expr)
            is SafeAccessExpr -> evalSafeAccess(expr)
            is ElvisExpr -> evalElvisExpr(expr)
            is TernaryExpr ->
                    if (isTruthy(evalExpression(expr.condition))) evalExpression(expr.consequent)
                    else evalExpression(expr.alternative)
            is PropertyAccessExpr -> evalPropertyAccess(expr)
            is CallExpr -> evalCallExpr(expr)
            is ArrayLiteral -> evalElements(expr.elements)
            is ObjectLiteral -> expr.pairs.mapValues { evalExpression(it.value) }
            is IndexExpr -> evalIndexExpression(expr)
            is SpreadExpr ->
                    throw RuntimeException(
                            "spread '...' is only valid inside arrays and call arguments"
                    )
        }
    }

    /** Evaluates expressions, expanding any ...spread elements into the result. */
    private fun evalElements(exprs: List<Expression>): List<Any?> {
        val result = mutableListOf<Any?>()
        for (el in exprs) {
            if (el is SpreadExpr) {
                val v = evalExpression(el.value)
                if (v is List<*>) result.addAll(v)
                else throw RuntimeException("spread operator requires an array")
            } else {
                result.add(evalExpression(el))
            }
        }
        return result
    }

    private fun evalIndexExpression(expr: IndexExpr): Any? {
        val left = evalExpression(expr.left)
        val index = evalExpression(expr.index)

        return when (left) {
            is List<*> -> {
                val idx = toNumber(index).toInt()
                if (idx < 0 || idx >= left.size) null else left[idx]
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST") val map = left as Map<String, Any?>
                val key = index.toString()
                map[key]
            }
            else ->
                    throw RuntimeException(
                            "index operator not supported: ${left?.javaClass?.simpleName}"
                    )
        }
    }

    private fun evalBinaryExpr(expr: BinaryExpr): Any? {
        val left = evalExpression(expr.left)

        // Short-circuit for && and ||
        when (expr.operator) {
            "&&" -> {
                if (!isTruthy(left)) return false
                return isTruthy(evalExpression(expr.right))
            }
            "||" -> {
                if (isTruthy(left)) return true
                return isTruthy(evalExpression(expr.right))
            }
        }

        val right = evalExpression(expr.right)

        return when (expr.operator) {
            "+" -> evalPlus(left, right)
            "-" -> evalArithmetic(left, right) { a, b -> a - b }
            "*" -> evalArithmetic(left, right) { a, b -> a * b }
            "/" -> {
                val r = toNumber(right)
                if (r == 0.0) throw RuntimeException("division by zero")
                evalArithmetic(left, right) { a, b -> a / b }
            }
            "%" -> {
                val r = toNumber(right)
                if (r == 0.0) throw RuntimeException("modulo by zero")
                evalArithmetic(left, right) { a, b -> a % b }
            }
            "==" -> left == right
            "!=" -> left != right
            "<" -> evalComparison(left, right) { a, b -> a < b }
            ">" -> evalComparison(left, right) { a, b -> a > b }
            "<=" -> evalComparison(left, right) { a, b -> a <= b }
            ">=" -> evalComparison(left, right) { a, b -> a >= b }
            else -> throw RuntimeException("unknown operator: ${expr.operator}")
        }
    }

    private fun evalPlus(left: Any?, right: Any?): Any {
        if (left is String || right is String) {
            return kodiStringify(left) + kodiStringify(right)
        }
        return toNumber(left) + toNumber(right)
    }

    private fun evalArithmetic(left: Any?, right: Any?, op: (Double, Double) -> Double): Double {
        return op(toNumber(left), toNumber(right))
    }

    private fun evalComparison(left: Any?, right: Any?, op: (Double, Double) -> Boolean): Boolean {
        return op(toNumber(left), toNumber(right))
    }

    private fun evalUnaryExpr(expr: UnaryExpr): Any? {
        val right = evalExpression(expr.right)
        return when (expr.operator) {
            "-" -> -toNumber(right)
            "!" -> !isTruthy(right)
            else -> throw RuntimeException("unknown unary operator: ${expr.operator}")
        }
    }

    private fun evalSafeAccess(expr: SafeAccessExpr): Any? {
        val obj = evalExpression(expr.obj)
        if (obj == null) return null

        @Suppress("UNCHECKED_CAST")
        if (obj is Map<*, *>) {
            return (obj as Map<String, Any?>)[expr.property.value]
        }
        return null
    }

    private fun evalElvisExpr(expr: ElvisExpr): Any? {
        val left = evalExpression(expr.left)
        return left ?: evalExpression(expr.default)
    }

    private fun evalPropertyAccess(expr: PropertyAccessExpr): Any? {
        val obj =
                evalExpression(expr.obj)
                        ?: throw RuntimeException(
                                "cannot access property '${expr.property.value}' on null"
                        )

        // First check for Map access (existing behavior)
        @Suppress("UNCHECKED_CAST")
        if (obj is Map<*, *>) {
            return (obj as Map<String, Any?>)[expr.property.value]
        }

        // Use reflection to access methods and properties on Kotlin objects
        return reflectivePropertyAccess(obj, expr.property.value)
    }

    /**
     * Built-in functions that need the interpreter itself (to capture output or
     * to call back into user functions). Held in a table — rather than
     * special-cased in [evalCallExpr] — so the dispatch is data-driven: adding a
     * new one no longer touches the evaluator, and scripts/hosts can override
     * them by name.
     */
    private val interpBuiltins: Map<String, (List<Any?>) -> Any?> by lazy {
        mapOf(
                "print" to ::builtinPrint,
                "map" to ::builtinMap,
                "filter" to ::builtinFilter,
                "reduce" to ::builtinReduce,
                "find" to ::builtinFind,
                "findIndex" to ::builtinFindIndex,
                "some" to ::builtinSome,
                "every" to ::builtinEvery,
                "flatMap" to ::builtinFlatMap
        )
    }

    private fun evalCallExpr(expr: CallExpr): Any? {
        val funcExpr = expr.function

        // Method-call syntax: receiver.method(args)
        if (funcExpr is PropertyAccessExpr) {
            return evalMethodCall(funcExpr, expr.arguments)
        }

        // Interpreter builtins (print, map, ...), unless overridden by a user
        // binding or a registered native of the same name.
        if (funcExpr is Identifier) {
            val builtin = interpBuiltins[funcExpr.value]
            if (builtin != null) {
                val inEnv = env.get(funcExpr.value) !== Environment.NotFound
                if (!inEnv && natives.get(funcExpr.value) == null) {
                    return builtin(evalElements(expr.arguments))
                }
            }
        }

        val function = evalExpression(funcExpr)
        val args = evalElements(expr.arguments)
        return applyFunction(function, args)
    }

    /** Implements method-call syntax: receiver.method(args). */
    private fun evalMethodCall(pa: PropertyAccessExpr, argExprs: List<Expression>): Any? {
        val receiver = evalExpression(pa.obj)
        val method = pa.property.value
        val args = evalElements(argExprs)

        // 1. A callable stored under that key on an object wins (obj.fn())
        if (receiver is Map<*, *>) {
            @Suppress("UNCHECKED_CAST") val m = receiver as Map<String, Any?>
            if (m.containsKey(method)) {
                val v = m[method]
                if (v is FunctionValue || v is NativeFunctionValue) {
                    return applyFunction(v, args)
                }
            }
        }

        // 2. Interpreter builtin invoked as a method: prepend the receiver
        interpBuiltins[method]?.let {
            return it(listOf(receiver) + args)
        }

        // 3. Registry native invoked as a method: prepend the receiver
        natives.get(method)?.let {
            return it(listOf(receiver) + args)
        }

        // 4. Bound object: method/field via reflection
        if (receiver == null) {
            throw RuntimeException("cannot call method '$method' on null")
        }
        if (receiver !is Map<*, *>) {
            return applyFunction(reflectivePropertyAccess(receiver, method), args)
        }

        throw RuntimeException("undefined method '$method'")
    }

    // ============ Interpreter builtins (need interpreter context) ============

    private fun builtinPrint(args: List<Any?>): Any? {
        args.forEach {
            val line = kodiStringify(it)
            val sink = outputSink
            if (sink != null) sink(line) else if (!silent) println(line)
            output.add(line)
        }
        return null
    }

    private fun builtinMap(args: List<Any?>): Any? {
        if (args.size < 2) throw RuntimeException("map requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return listOf<Any?>()
        val fnVal = args[1]
        return arr.mapIndexed { idx, item -> applyFunction(fnVal, listOf(item, idx.toDouble())) }
    }

    private fun builtinFilter(args: List<Any?>): Any? {
        if (args.size < 2) throw RuntimeException("filter requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return listOf<Any?>()
        val fnVal = args[1]
        return arr.filterIndexed { idx, item ->
            isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))
        }
    }

    private fun builtinReduce(args: List<Any?>): Any? {
        if (args.size < 3)
                throw RuntimeException(
                        "reduce requires 3 arguments: array, function, and initial value"
                )
        val arr = args[0] as? List<*> ?: return null
        val fnVal = args[1]
        var accumulator = args[2]
        arr.forEachIndexed { idx, item ->
            accumulator = applyFunction(fnVal, listOf(accumulator, item, idx.toDouble()))
        }
        return accumulator
    }

    private fun builtinFind(args: List<Any?>): Any? {
        if (args.size < 2) throw RuntimeException("find requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return null
        val fnVal = args[1]
        arr.forEachIndexed { idx, item ->
            if (isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))) {
                return item
            }
        }
        return null
    }

    private fun builtinFindIndex(args: List<Any?>): Any? {
        if (args.size < 2)
                throw RuntimeException("findIndex requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return -1.0
        val fnVal = args[1]
        arr.forEachIndexed { idx, item ->
            if (isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))) {
                return idx.toDouble()
            }
        }
        return -1.0
    }

    private fun builtinSome(args: List<Any?>): Any? {
        if (args.size < 2) throw RuntimeException("some requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return false
        val fnVal = args[1]
        arr.forEachIndexed { idx, item ->
            if (isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))) return true
        }
        return false
    }

    private fun builtinEvery(args: List<Any?>): Any? {
        if (args.size < 2) throw RuntimeException("every requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return true
        val fnVal = args[1]
        arr.forEachIndexed { idx, item ->
            if (!isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))) return false
        }
        return true
    }

    private fun builtinFlatMap(args: List<Any?>): Any? {
        if (args.size < 2) throw RuntimeException("flatMap requires 2 arguments: array and function")
        val arr = args[0] as? List<*> ?: return listOf<Any?>()
        val fnVal = args[1]
        val result = mutableListOf<Any?>()
        arr.forEachIndexed { idx, item ->
            val v = applyFunction(fnVal, listOf(item, idx.toDouble()))
            if (v is List<*>) result.addAll(v) else result.add(v)
        }
        return result
    }

    private fun applyFunction(fn: Any?, args: List<Any?>): Any? {
        return when (fn) {
            is FunctionValue -> {
                if (callDepth >= MAX_CALL_DEPTH) throw MaxCallDepthExceeded()
                callDepth++
                val extendedEnv = Environment(fn.env)
                for ((idx, param) in fn.parameters.withIndex()) {
                    if (idx < args.size) {
                        extendedEnv.set(param.value, args[idx])
                    }
                }

                val previousEnv = this.env
                this.env = extendedEnv
                try {
                    val result = evalBlockStatement(fn.body)
                    when {
                        result is ReturnValue -> result.value
                        // A stray break/continue must not escape the function as a value.
                        result === BreakSignal || result === ContinueSignal -> null
                        else -> result
                    }
                } finally {
                    this.env = previousEnv
                    callDepth--
                }
            }
            is NativeFunctionValue -> fn.fn(args)
            else -> throw RuntimeException("not a function: ${fn?.javaClass?.simpleName}")
        }
    }

    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        return true
    }

    private fun toNumber(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    // ============ Reflection Support ============

    /** Uses Kotlin reflection to access properties and methods on objects. */
    private fun reflectivePropertyAccess(obj: Any, propertyName: String): Any? {
        val kClass = obj::class

        // Resolve (and cache) how this name binds on this class. Methods have
        // priority over properties.
        val member =
                reflectCache.getOrPut(kClass to propertyName) {
                    val method = kClass.memberFunctions.find { it.name == propertyName }
                    if (method != null) {
                        ReflectMember.Method(method)
                    } else {
                        val property = kClass.memberProperties.find { it.name == propertyName }
                        if (property != null) {
                            property.isAccessible = true
                            ReflectMember.Property(property)
                        } else {
                            ReflectMember.None
                        }
                    }
                }

        return when (member) {
            is ReflectMember.Method ->
                    NativeFunctionValue { args -> callReflectedMethod(obj, member.fn, args) }
            is ReflectMember.Property -> convertFromKotlinType(member.prop.call(obj))
            ReflectMember.None ->
                    throw RuntimeException(
                            "property or method '$propertyName' not found on ${kClass.simpleName}"
                    )
        }
    }

    /** Calls a Kotlin method via reflection with argument type conversion. */
    private fun callReflectedMethod(instance: Any, method: KCallable<*>, args: List<Any?>): Any? {
        try {
            method.isAccessible = true

            // Convert arguments to match parameter types
            val parameters = method.parameters
            val convertedArgs = mutableListOf<Any?>()

            // First parameter is the instance (for member functions)
            convertedArgs.add(instance)

            // Convert remaining arguments
            for (i in args.indices) {
                if (i + 1 < parameters.size) {
                    val param = parameters[i + 1]
                    val arg = args[i]
                    convertedArgs.add(convertToKotlinType(arg, param.type))
                } else {
                    convertedArgs.add(args[i])
                }
            }

            val result = method.call(*convertedArgs.toTypedArray())
            return convertFromKotlinType(result)
        } catch (e: Exception) {
            throw RuntimeException("error calling method '${method.name}': ${e.message}", e)
        }
    }

    /** Converts a KodiScript value to the target Kotlin type. */
    private fun convertToKotlinType(value: Any?, targetType: kotlin.reflect.KType): Any? {
        if (value == null) return null

        val classifier = targetType.classifier
        if (classifier !is KClass<*>) return value

        return when (classifier) {
            Int::class ->
                    when (value) {
                        is Double -> value.toInt()
                        is Int -> value
                        else -> value.toString().toIntOrNull() ?: 0
                    }
            Long::class ->
                    when (value) {
                        is Double -> value.toLong()
                        is Int -> value.toLong()
                        is Long -> value
                        else -> value.toString().toLongOrNull() ?: 0L
                    }
            Float::class ->
                    when (value) {
                        is Double -> value.toFloat()
                        is Float -> value
                        else -> value.toString().toFloatOrNull() ?: 0f
                    }
            Double::class -> toNumber(value)
            String::class -> value.toString()
            Boolean::class ->
                    when (value) {
                        is Boolean -> value
                        else -> isTruthy(value)
                    }
            else -> value // Return as-is for complex types
        }
    }

    /** Converts a Kotlin value to a KodiScript-compatible value. */
    private fun convertFromKotlinType(value: Any?): Any? {
        return when (value) {
            null -> null
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            is Short -> value.toDouble()
            is Byte -> value.toDouble()
            else -> value // String, Double, Boolean, custom objects
        }
    }
}
