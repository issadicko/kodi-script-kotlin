package com.kodi.script.ast

import com.kodi.script.token.Token

/** Base interface for all AST nodes. */
sealed interface Node {
    fun tokenLiteral(): String
}

/** Represents a statement node. */
sealed interface Statement : Node

/** Represents an expression node. */
sealed interface Expression : Node

/** Program is the root node of every AST. */
data class Program(val statements: MutableList<Statement> = mutableListOf()) : Node {
    override fun tokenLiteral(): String = statements.firstOrNull()?.tokenLiteral() ?: ""
}

/** Variable declaration: let x = expr */
data class VarDecl(val token: Token, val name: Identifier, val value: Expression) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Assignment: x = expr */
data class Assignment(val token: Token, val name: Identifier, val value: Expression) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Array destructuring: let [a, b, c] = expr */
data class ArrayDestructure(val token: Token, val names: List<Identifier>, val value: Expression) :
        Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Object destructuring: let {a, b} = expr (binds keys a and b) */
data class ObjectDestructure(val token: Token, val names: List<Identifier>, val value: Expression) :
        Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Expression statement wraps an expression. */
data class ExpressionStatement(val token: Token, val expression: Expression) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** If statement: if (condition) { consequence } else { alternative } */
data class IfStatement(
        val token: Token,
        val condition: Expression,
        val consequence: BlockStatement,
        val alternative: BlockStatement? = null
) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Block of statements: { ... } */
data class BlockStatement(
        val token: Token,
        val statements: MutableList<Statement> = mutableListOf()
) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Return statement: return [expr] */
data class ReturnStatement(val token: Token, val value: Expression? = null) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** For statement: for (variable in iterable) { body } */
data class ForStatement(
        val token: Token,
        val variable: Identifier,
        val iterable: Expression,
        val body: BlockStatement
) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** While statement: while (condition) { body } */
data class WhileStatement(val token: Token, val condition: Expression, val body: BlockStatement) :
        Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Try/catch statement: try { body } catch (e) { handler } */
data class TryStatement(
        val token: Token,
        val body: BlockStatement,
        val catchVar: Identifier?,
        val catch: BlockStatement
) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Break statement: break (exits the nearest enclosing loop). */
data class BreakStatement(val token: Token) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Continue statement: continue (skips to the next loop iteration). */
data class ContinueStatement(val token: Token) : Statement {
    override fun tokenLiteral(): String = token.literal
}

/** Variable identifier. */
data class Identifier(val token: Token, val value: String) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Numeric literal. */
data class NumberLiteral(val token: Token, val value: Double) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** String literal. */
data class StringLiteral(val token: Token, val value: String) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** String template: "Hello ${name}!" */
data class StringTemplate(val token: Token, val parts: List<Expression>) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Boolean literal (true/false). */
data class BooleanLiteral(val token: Token, val value: Boolean) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Null literal. */
data class NullLiteral(val token: Token) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Array literal: [elem1, elem2, ...] */
data class ArrayLiteral(val token: Token, val elements: List<Expression>) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Object literal: {key: value, ...} */
data class ObjectLiteral(val token: Token, val pairs: Map<String, Expression>) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Index expression: left[index] */
data class IndexExpr(val token: Token, val left: Expression, val index: Expression) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Binary expression: left op right */
data class BinaryExpr(
        val token: Token,
        val left: Expression,
        val operator: String,
        val right: Expression
) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Unary expression: op expr */
data class UnaryExpr(val token: Token, val operator: String, val right: Expression) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Safe access: obj?.property */
data class SafeAccessExpr(val token: Token, val obj: Expression, val property: Identifier) :
        Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Elvis expression: expr ?: default */
data class ElvisExpr(val token: Token, val left: Expression, val default: Expression) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Spread element: ...expr (inside array literals and call arguments). */
data class SpreadExpr(val token: Token, val value: Expression) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Ternary conditional: condition ? consequent : alternative */
data class TernaryExpr(
        val token: Token,
        val condition: Expression,
        val consequent: Expression,
        val alternative: Expression
) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Property access: obj.property */
data class PropertyAccessExpr(val token: Token, val obj: Expression, val property: Identifier) :
        Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Function literal: fn(x, y) { ... } */
data class FunctionLiteral(
        val token: Token,
        val parameters: List<Identifier>,
        val body: BlockStatement
) : Expression {
    override fun tokenLiteral(): String = token.literal
}

/** Function call: func(args...) */
data class CallExpr(val token: Token, val function: Expression, val arguments: List<Expression>) :
        Expression {
    override fun tokenLiteral(): String = token.literal
}
