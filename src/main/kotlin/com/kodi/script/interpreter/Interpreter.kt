package com.kodi.script.interpreter

import com.kodi.script.ast.*
import com.kodi.script.natives.NativeFunctions

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

/** Interpreter evaluates AST nodes. */
class Interpreter(
        private val env: Environment = Environment(),
        private val natives: NativeFunctions = NativeFunctions()
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

    private fun evalBlockStatement(block: BlockStatement): Any? {
        var result: Any? = null
        for (stmt in block.statements) {
            result = evalStatement(stmt)
        }
        return result
    }

    private fun evalExpression(expr: Expression): Any? {
        return when (expr) {
            is NumberLiteral -> expr.value
            is StringLiteral -> expr.value
            is BooleanLiteral -> expr.value
            is NullLiteral -> null
            is Identifier -> {
                val (value, found) = env.get(expr.value)
                if (!found) throw RuntimeException("undefined variable: ${expr.value}")
                value
            }
            is BinaryExpr -> evalBinaryExpr(expr)
            is UnaryExpr -> evalUnaryExpr(expr)
            is SafeAccessExpr -> evalSafeAccess(expr)
            is ElvisExpr -> evalElvisExpr(expr)
            is PropertyAccessExpr -> evalPropertyAccess(expr)
            is CallExpr -> evalCallExpr(expr)
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

        @Suppress("UNCHECKED_CAST")
        if (obj is Map<*, *>) {
            return (obj as Map<String, Any?>)[expr.property.value]
        }
        throw RuntimeException("cannot access property on ${obj::class.simpleName}")
    }

    private fun evalCallExpr(expr: CallExpr): Any? {
        val funcExpr = expr.function
        if (funcExpr !is Identifier) {
            throw RuntimeException("expected function identifier")
        }

        val args = expr.arguments.map { evalExpression(it) }

        // Handle print specially
        if (funcExpr.value == "print") {
            args.forEach { env.addOutput(it?.toString() ?: "null") }
            return null
        }

        val fn =
                natives.get(funcExpr.value)
                        ?: throw RuntimeException("undefined function: ${funcExpr.value}")

        return fn(args)
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
}
