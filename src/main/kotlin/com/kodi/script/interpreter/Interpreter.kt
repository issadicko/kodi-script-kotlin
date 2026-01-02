package com.kodi.script.interpreter

import com.kodi.script.ast.*
import com.kodi.script.natives.NativeFunctions
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/** Environment holds variable bindings. */
class Environment(private val outer: Environment? = null) {
    private val store = mutableMapOf<String, Any?>()
    private val output = mutableListOf<String>()

    fun get(name: String): Pair<Any?, Boolean> {
        val value = store[name]
        if (value != null || store.containsKey(name)) {
            return value to true
        }
        return outer?.get(name) ?: (null to false)
    }

    fun set(name: String, value: Any?) {
        store[name] = value
    }

    fun addOutput(line: String) {
        output.add(line)
    }

    fun getOutput(): List<String> = output
}

/** Wrapper to signal early return from evaluation. */
class ReturnValue(val value: Any?)

/** Function value (user defined). */
data class FunctionValue(
        val parameters: List<Identifier>,
        val body: BlockStatement,
        val env: Environment
)

/** Native function wrapper. */
data class NativeFunctionValue(val fn: (List<Any?>) -> Any?)

/** Interpreter evaluates AST nodes. */
class Interpreter(
        private var env: Environment = Environment(),
        private val natives: NativeFunctions = NativeFunctions.shared
) {

    companion object {
        fun withVariables(variables: Map<String, Any?>): Interpreter {
            val env = Environment()
            variables.forEach { (k, v) -> env.set(k, v) }
            return Interpreter(env)
        }
    }

    fun eval(program: Program): Any? {
        var result: Any? = null
        for (stmt in program.statements) {
            result = evalStatement(stmt)
            // Unwrap return values at top level
            if (result is ReturnValue) {
                return result.value
            }
        }
        return result
    }

    fun getOutput(): List<String> = env.getOutput()

    private fun evalStatement(stmt: Statement): Any? {
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
            is ExpressionStatement -> evalExpression(stmt.expression)
            is IfStatement -> evalIfStatement(stmt)
            is BlockStatement -> evalBlockStatement(stmt)
            is ReturnStatement -> {
                val value = stmt.value?.let { evalExpression(it) }
                ReturnValue(value)
            }
            is ForStatement -> evalForStatement(stmt)
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
            // Set loop variable
            env.set(varName, item)

            // Execute body
            val value = evalBlockStatement(stmt.body)

            // Check for return
            if (value is ReturnValue) {
                return value
            }

            result = value
        }

        return result
    }

    private fun evalBlockStatement(block: BlockStatement): Any? {
        var result: Any? = null
        for (stmt in block.statements) {
            result = evalStatement(stmt)
            // Propagate return values up the call stack
            if (result is ReturnValue) {
                return result
            }
        }
        return result
    }

    private fun evalStringTemplate(tmpl: StringTemplate): Any {
        val result = StringBuilder()
        for (part in tmpl.parts) {
            val value = evalExpression(part)
            if (value == null) {
                result.append("null")
            } else {
                result.append(value.toString())
            }
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
                val (value, found) = env.get(expr.value)
                if (found) return value

                val native = natives.get(expr.value)
                if (native != null) return NativeFunctionValue(native)

                throw RuntimeException("undefined variable: ${expr.value}")
            }
            is FunctionLiteral -> FunctionValue(expr.parameters, expr.body, env)
            is BinaryExpr -> evalBinaryExpr(expr)
            is UnaryExpr -> evalUnaryExpr(expr)
            is SafeAccessExpr -> evalSafeAccess(expr)
            is ElvisExpr -> evalElvisExpr(expr)
            is PropertyAccessExpr -> evalPropertyAccess(expr)
            is CallExpr -> evalCallExpr(expr)
            is ArrayLiteral -> expr.elements.map { evalExpression(it) }
            is ObjectLiteral -> expr.pairs.mapValues { evalExpression(it.value) }
            is IndexExpr -> evalIndexExpression(expr)
        }
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
            return "${left ?: "null"}${right ?: "null"}"
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

    private fun evalCallExpr(expr: CallExpr): Any? {
        val funcExpr = expr.function
        // Special print handling
        if (funcExpr is Identifier && funcExpr.value == "print") {
            val args = expr.arguments.map { evalExpression(it) }
            args.forEach {
                val output = it?.toString() ?: "null"
                println(output)
                env.addOutput(output)
            }
            return null
        }

        // Special handling for higher-order array functions
        if (funcExpr is Identifier) {
            when (funcExpr.value) {
                "map" -> return evalMapFunction(expr)
                "filter" -> return evalFilterFunction(expr)
                "reduce" -> return evalReduceFunction(expr)
                "find" -> return evalFindFunction(expr)
                "findIndex" -> return evalFindIndexFunction(expr)
            }
        }

        val function = evalExpression(funcExpr)
        val args = expr.arguments.map { evalExpression(it) }

        return applyFunction(function, args)
    }

    private fun evalMapFunction(expr: CallExpr): Any? {
        if (expr.arguments.size < 2)
                throw RuntimeException("map requires 2 arguments: array and function")
        val arrVal = evalExpression(expr.arguments[0])
        val arr = arrVal as? List<*> ?: return listOf<Any?>()
        val fnVal = evalExpression(expr.arguments[1])
        return arr.mapIndexed { idx, item -> applyFunction(fnVal, listOf(item, idx.toDouble())) }
    }

    private fun evalFilterFunction(expr: CallExpr): Any? {
        if (expr.arguments.size < 2)
                throw RuntimeException("filter requires 2 arguments: array and function")
        val arrVal = evalExpression(expr.arguments[0])
        val arr = arrVal as? List<*> ?: return listOf<Any?>()
        val fnVal = evalExpression(expr.arguments[1])
        return arr.filterIndexed { idx, item ->
            isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))
        }
    }

    private fun evalReduceFunction(expr: CallExpr): Any? {
        if (expr.arguments.size < 3)
                throw RuntimeException(
                        "reduce requires 3 arguments: array, function, and initial value"
                )
        val arrVal = evalExpression(expr.arguments[0])
        val arr = arrVal as? List<*> ?: return null
        val fnVal = evalExpression(expr.arguments[1])
        var accumulator = evalExpression(expr.arguments[2])
        arr.forEachIndexed { idx, item ->
            accumulator = applyFunction(fnVal, listOf(accumulator, item, idx.toDouble()))
        }
        return accumulator
    }

    private fun evalFindFunction(expr: CallExpr): Any? {
        if (expr.arguments.size < 2)
                throw RuntimeException("find requires 2 arguments: array and function")
        val arrVal = evalExpression(expr.arguments[0])
        val arr = arrVal as? List<*> ?: return null
        val fnVal = evalExpression(expr.arguments[1])
        arr.forEachIndexed { idx, item ->
            if (isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))) {
                return item
            }
        }
        return null
    }

    private fun evalFindIndexFunction(expr: CallExpr): Any? {
        if (expr.arguments.size < 2)
                throw RuntimeException("findIndex requires 2 arguments: array and function")
        val arrVal = evalExpression(expr.arguments[0])
        val arr = arrVal as? List<*> ?: return -1.0
        val fnVal = evalExpression(expr.arguments[1])
        arr.forEachIndexed { idx, item ->
            if (isTruthy(applyFunction(fnVal, listOf(item, idx.toDouble())))) {
                return idx.toDouble()
            }
        }
        return -1.0
    }

    private fun applyFunction(fn: Any?, args: List<Any?>): Any? {
        return when (fn) {
            is FunctionValue -> {
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
                    if (result is ReturnValue) result.value else result
                } finally {
                    this.env = previousEnv
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

        // Try to find a method first (methods have priority over properties)
        val method = kClass.memberFunctions.find { it.name == propertyName }
        if (method != null) {
            // Return a callable wrapper
            return NativeFunctionValue { args -> callReflectedMethod(obj, method, args) }
        }

        // Try to access a property
        val property = kClass.memberProperties.find { it.name == propertyName }
        if (property != null) {
            property.isAccessible = true
            return convertFromKotlinType(property.call(obj))
        }

        throw RuntimeException(
                "property or method '$propertyName' not found on ${kClass.simpleName}"
        )
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
