package com.kodi.script.parser

import com.kodi.script.ast.*
import com.kodi.script.lexer.Lexer
import com.kodi.script.token.Token
import com.kodi.script.token.TokenType

/** Parser parses tokens from a Lexer into an AST. */
class Parser(private val lexer: Lexer) {
    private var curToken: Token = Token(TokenType.EOF, "")
    private var peekToken: Token = Token(TokenType.EOF, "")
    private val errors: MutableList<String> = mutableListOf()

    private val prefixParseFns = mutableMapOf<TokenType, () -> Expression?>()
    private val infixParseFns = mutableMapOf<TokenType, (Expression) -> Expression?>()

    init {
        // Register prefix parse functions
        prefixParseFns[TokenType.IDENT] = ::parseIdentifier
        prefixParseFns[TokenType.NUMBER] = ::parseNumberLiteral
        prefixParseFns[TokenType.STRING] = ::parseStringLiteral
        prefixParseFns[TokenType.TRUE] = ::parseBooleanLiteral
        prefixParseFns[TokenType.FALSE] = ::parseBooleanLiteral
        prefixParseFns[TokenType.NULL] = ::parseNullLiteral
        prefixParseFns[TokenType.MINUS] = ::parsePrefixExpression
        prefixParseFns[TokenType.NOT] = ::parsePrefixExpression
        prefixParseFns[TokenType.LPAREN] = ::parseGroupedExpression

        // Register infix parse functions
        infixParseFns[TokenType.PLUS] = ::parseInfixExpression
        infixParseFns[TokenType.MINUS] = ::parseInfixExpression
        infixParseFns[TokenType.ASTERISK] = ::parseInfixExpression
        infixParseFns[TokenType.SLASH] = ::parseInfixExpression
        infixParseFns[TokenType.EQ] = ::parseInfixExpression
        infixParseFns[TokenType.NOT_EQ] = ::parseInfixExpression
        infixParseFns[TokenType.LT] = ::parseInfixExpression
        infixParseFns[TokenType.GT] = ::parseInfixExpression
        infixParseFns[TokenType.LT_EQ] = ::parseInfixExpression
        infixParseFns[TokenType.GT_EQ] = ::parseInfixExpression
        infixParseFns[TokenType.AND] = ::parseInfixExpression
        infixParseFns[TokenType.OR] = ::parseInfixExpression
        infixParseFns[TokenType.ELVIS] = ::parseElvisExpression
        infixParseFns[TokenType.DOT] = ::parsePropertyAccess
        infixParseFns[TokenType.SAFE_ACCESS] = ::parseSafeAccess
        infixParseFns[TokenType.LPAREN] = ::parseCallExpression

        // Initialize tokens
        nextToken()
        nextToken()
    }

    fun errors(): List<String> = errors

    private fun addError(format: String, vararg args: Any) {
        errors.add("line ${curToken.line}, col ${curToken.column}: ${format.format(*args)}")
    }

    private fun nextToken() {
        curToken = peekToken
        peekToken = lexer.nextToken()
    }

    private fun curTokenIs(type: TokenType): Boolean = curToken.type == type
    private fun peekTokenIs(type: TokenType): Boolean = peekToken.type == type

    private fun expectPeek(type: TokenType): Boolean {
        return if (peekTokenIs(type)) {
            nextToken()
            true
        } else {
            addError("expected %s, got %s", type, peekToken.type)
            false
        }
    }

    private fun peekPrecedence(): Int = precedences[peekToken.type] ?: LOWEST
    private fun curPrecedence(): Int = precedences[curToken.type] ?: LOWEST

    private fun consumeEndOfStatement() {
        while (curTokenIs(TokenType.SEMICOLON) || curTokenIs(TokenType.NEWLINE)) {
            nextToken()
        }
    }

    fun parseProgram(): Program {
        val program = Program()

        while (!curTokenIs(TokenType.EOF)) {
            consumeEndOfStatement()
            if (curTokenIs(TokenType.EOF)) break

            parseStatement()?.let { program.statements.add(it) }
            // Move to the next token after parsing a statement
            if (!curTokenIs(TokenType.EOF) &&
                            !curTokenIs(TokenType.SEMICOLON) &&
                            !curTokenIs(TokenType.NEWLINE)
            ) {
                nextToken()
            }
            consumeEndOfStatement()
        }

        return program
    }

    private fun parseStatement(): Statement? {
        return when (curToken.type) {
            TokenType.LET -> parseVarDecl()
            TokenType.IF -> parseIfStatement()
            TokenType.IDENT -> {
                if (peekTokenIs(TokenType.ASSIGN)) parseAssignment() else parseExpressionStatement()
            }
            else -> parseExpressionStatement()
        }
    }

    private fun parseVarDecl(): VarDecl? {
        val token = curToken

        if (!expectPeek(TokenType.IDENT)) return null
        val name = Identifier(curToken, curToken.literal)

        if (!expectPeek(TokenType.ASSIGN)) return null
        nextToken()

        val value = parseExpression(LOWEST) ?: return null

        return VarDecl(token, name, value)
    }

    private fun parseAssignment(): Assignment? {
        val token = curToken
        val name = Identifier(curToken, curToken.literal)

        nextToken() // consume ASSIGN
        nextToken() // move to expression

        val value = parseExpression(LOWEST) ?: return null

        return Assignment(token, name, value)
    }

    private fun parseIfStatement(): IfStatement? {
        val token = curToken

        if (!expectPeek(TokenType.LPAREN)) return null
        nextToken()

        val condition = parseExpression(LOWEST) ?: return null

        if (!expectPeek(TokenType.RPAREN)) return null
        if (!expectPeek(TokenType.LBRACE)) return null

        val consequence = parseBlockStatement()

        var alternative: BlockStatement? = null
        if (peekTokenIs(TokenType.ELSE)) {
            nextToken()
            if (!expectPeek(TokenType.LBRACE)) return null
            alternative = parseBlockStatement()
        }

        return IfStatement(token, condition, consequence, alternative)
    }

    private fun parseBlockStatement(): BlockStatement {
        val block = BlockStatement(curToken)
        nextToken()

        while (!curTokenIs(TokenType.RBRACE) && !curTokenIs(TokenType.EOF)) {
            consumeEndOfStatement()
            if (curTokenIs(TokenType.RBRACE)) break

            parseStatement()?.let { block.statements.add(it) }
            // Move to the next token after parsing a statement
            if (!curTokenIs(TokenType.EOF) &&
                            !curTokenIs(TokenType.RBRACE) &&
                            !curTokenIs(TokenType.SEMICOLON) &&
                            !curTokenIs(TokenType.NEWLINE)
            ) {
                nextToken()
            }
            consumeEndOfStatement()
        }

        return block
    }

    private fun parseExpressionStatement(): ExpressionStatement? {
        val token = curToken
        val expression = parseExpression(LOWEST) ?: return null
        return ExpressionStatement(token, expression)
    }

    private fun parseExpression(precedence: Int): Expression? {
        val prefix = prefixParseFns[curToken.type]
        if (prefix == null) {
            addError("no prefix parse function for %s", curToken.type)
            return null
        }

        var leftExp = prefix() ?: return null

        while (!peekTokenIs(TokenType.SEMICOLON) &&
                !peekTokenIs(TokenType.NEWLINE) &&
                !peekTokenIs(TokenType.EOF) &&
                precedence < peekPrecedence()) {
            val infix = infixParseFns[peekToken.type] ?: return leftExp
            nextToken()
            leftExp = infix(leftExp) ?: return null
        }

        return leftExp
    }

    private fun parseIdentifier(): Expression = Identifier(curToken, curToken.literal)

    private fun parseNumberLiteral(): Expression? {
        val value = curToken.literal.toDoubleOrNull()
        if (value == null) {
            addError("could not parse '%s' as number", curToken.literal)
            return null
        }
        return NumberLiteral(curToken, value)
    }

    private fun parseStringLiteral(): Expression = StringLiteral(curToken, curToken.literal)

    private fun parseBooleanLiteral(): Expression =
            BooleanLiteral(curToken, curTokenIs(TokenType.TRUE))

    private fun parseNullLiteral(): Expression = NullLiteral(curToken)

    private fun parsePrefixExpression(): Expression? {
        val token = curToken
        val operator = curToken.literal
        nextToken()
        val right = parseExpression(PREFIX) ?: return null
        return UnaryExpr(token, operator, right)
    }

    private fun parseGroupedExpression(): Expression? {
        nextToken()
        val exp = parseExpression(LOWEST)
        if (!expectPeek(TokenType.RPAREN)) return null
        return exp
    }

    private fun parseInfixExpression(left: Expression): Expression? {
        val token = curToken
        val operator = curToken.literal
        val precedence = curPrecedence()
        nextToken()
        val right = parseExpression(precedence) ?: return null
        return BinaryExpr(token, left, operator, right)
    }

    private fun parseElvisExpression(left: Expression): Expression? {
        val token = curToken
        nextToken()
        val default = parseExpression(ELVIS) ?: return null
        return ElvisExpr(token, left, default)
    }

    private fun parsePropertyAccess(left: Expression): Expression? {
        val token = curToken
        if (!expectPeek(TokenType.IDENT)) return null
        val property = Identifier(curToken, curToken.literal)
        return PropertyAccessExpr(token, left, property)
    }

    private fun parseSafeAccess(left: Expression): Expression? {
        val token = curToken
        if (!expectPeek(TokenType.IDENT)) return null
        val property = Identifier(curToken, curToken.literal)
        return SafeAccessExpr(token, left, property)
    }

    private fun parseCallExpression(function: Expression): Expression? {
        val token = curToken
        val arguments = parseCallArguments() ?: return null
        return CallExpr(token, function, arguments)
    }

    private fun parseCallArguments(): List<Expression>? {
        val args = mutableListOf<Expression>()

        if (peekTokenIs(TokenType.RPAREN)) {
            nextToken()
            return args
        }

        nextToken()
        args.add(parseExpression(LOWEST) ?: return null)

        while (peekTokenIs(TokenType.COMMA)) {
            nextToken()
            nextToken()
            args.add(parseExpression(LOWEST) ?: return null)
        }

        if (!expectPeek(TokenType.RPAREN)) return null

        return args
    }

    companion object {
        private const val LOWEST = 1
        private const val ELVIS = 2
        private const val OR = 3
        private const val AND = 4
        private const val EQUALS = 5
        private const val LESSGREATER = 6
        private const val SUM = 7
        private const val PRODUCT = 8
        private const val PREFIX = 9
        private const val CALL = 10
        private const val ACCESS = 11

        private val precedences =
                mapOf(
                        TokenType.ELVIS to ELVIS,
                        TokenType.OR to OR,
                        TokenType.AND to AND,
                        TokenType.EQ to EQUALS,
                        TokenType.NOT_EQ to EQUALS,
                        TokenType.LT to LESSGREATER,
                        TokenType.GT to LESSGREATER,
                        TokenType.LT_EQ to LESSGREATER,
                        TokenType.GT_EQ to LESSGREATER,
                        TokenType.PLUS to SUM,
                        TokenType.MINUS to SUM,
                        TokenType.ASTERISK to PRODUCT,
                        TokenType.SLASH to PRODUCT,
                        TokenType.LPAREN to CALL,
                        TokenType.DOT to ACCESS,
                        TokenType.SAFE_ACCESS to ACCESS
                )
    }
}
