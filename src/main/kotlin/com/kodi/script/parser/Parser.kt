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
        prefixParseFns[TokenType.STRING_TEMPLATE] = ::parseStringTemplate
        prefixParseFns[TokenType.TRUE] = ::parseBooleanLiteral
        prefixParseFns[TokenType.FALSE] = ::parseBooleanLiteral
        prefixParseFns[TokenType.NULL] = ::parseNullLiteral
        prefixParseFns[TokenType.MINUS] = ::parsePrefixExpression
        prefixParseFns[TokenType.NOT] = ::parsePrefixExpression
        prefixParseFns[TokenType.LPAREN] = ::parseGroupedExpression
        prefixParseFns[TokenType.LBRACKET] = ::parseArrayLiteral
        prefixParseFns[TokenType.LBRACE] = ::parseObjectLiteral
        prefixParseFns[TokenType.FN] = ::parseFunctionLiteral

        // Register infix parse functions
        infixParseFns[TokenType.PLUS] = ::parseInfixExpression
        infixParseFns[TokenType.MINUS] = ::parseInfixExpression
        infixParseFns[TokenType.ASTERISK] = ::parseInfixExpression
        infixParseFns[TokenType.SLASH] = ::parseInfixExpression
        infixParseFns[TokenType.PERCENT] = ::parseInfixExpression
        infixParseFns[TokenType.EQ] = ::parseInfixExpression
        infixParseFns[TokenType.NOT_EQ] = ::parseInfixExpression
        infixParseFns[TokenType.LT] = ::parseInfixExpression
        infixParseFns[TokenType.GT] = ::parseInfixExpression
        infixParseFns[TokenType.LT_EQ] = ::parseInfixExpression
        infixParseFns[TokenType.GT_EQ] = ::parseInfixExpression
        infixParseFns[TokenType.AND] = ::parseInfixExpression
        infixParseFns[TokenType.OR] = ::parseInfixExpression
        infixParseFns[TokenType.ELVIS] = ::parseElvisExpression
        infixParseFns[TokenType.QUESTION] = ::parseTernaryExpression
        infixParseFns[TokenType.DOT] = ::parsePropertyAccess
        infixParseFns[TokenType.SAFE_ACCESS] = ::parseSafeAccess
        infixParseFns[TokenType.LPAREN] = ::parseCallExpression
        infixParseFns[TokenType.LBRACKET] = ::parseIndexExpression

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
            TokenType.RETURN -> parseReturnStatement()
            TokenType.FOR -> parseForStatement()
            TokenType.WHILE -> parseWhileStatement()
            TokenType.TRY -> parseTryStatement()
            TokenType.BREAK -> BreakStatement(curToken)
            TokenType.CONTINUE -> ContinueStatement(curToken)
            TokenType.FN ->
                    if (peekTokenIs(TokenType.IDENT)) parseFunctionDeclaration()
                    else parseExpressionStatement()
            TokenType.IDENT ->
                    when (peekToken.type) {
                        TokenType.ASSIGN -> parseAssignment()
                        TokenType.PLUS_EQ,
                        TokenType.MINUS_EQ,
                        TokenType.ASTERISK_EQ,
                        TokenType.SLASH_EQ -> parseCompoundAssignment()
                        TokenType.PLUS_PLUS,
                        TokenType.MINUS_MINUS -> parseIncDec()
                        else -> parseExpressionStatement()
                    }
            else -> parseExpressionStatement()
        }
    }

    private fun parseCompoundAssignment(): Assignment? {
        val token = curToken
        val name = Identifier(curToken, curToken.literal)

        nextToken() // move onto the compound-assign operator
        val opToken = curToken
        val op =
                when (opToken.type) {
                    TokenType.PLUS_EQ -> "+"
                    TokenType.MINUS_EQ -> "-"
                    TokenType.ASTERISK_EQ -> "*"
                    TokenType.SLASH_EQ -> "/"
                    else -> return null
                }

        nextToken() // move onto the right-hand expression
        val right = parseExpression(LOWEST) ?: return null

        return Assignment(token, name, BinaryExpr(opToken, name, op, right))
    }

    private fun parseIncDec(): Assignment {
        val token = curToken
        val name = Identifier(curToken, curToken.literal)

        nextToken() // move onto ++ / -- ; caller advances past it
        val opToken = curToken
        val op = if (opToken.type == TokenType.MINUS_MINUS) "-" else "+"

        val one = NumberLiteral(Token(TokenType.NUMBER, "1"), 1.0)
        return Assignment(token, name, BinaryExpr(opToken, name, op, one))
    }

    private fun parseReturnStatement(): ReturnStatement {
        val token = curToken

        // Check if there's an expression after return
        if (peekTokenIs(TokenType.SEMICOLON) ||
                        peekTokenIs(TokenType.NEWLINE) ||
                        peekTokenIs(TokenType.EOF) ||
                        peekTokenIs(TokenType.RBRACE)
        ) {
            return ReturnStatement(token, null)
        }

        nextToken()
        val value = parseExpression(LOWEST)
        return ReturnStatement(token, value)
    }

    private fun parseForStatement(): ForStatement? {
        val token = curToken

        // Expect (
        if (!expectPeek(TokenType.LPAREN)) return null

        // Expect identifier (loop variable)
        if (!expectPeek(TokenType.IDENT)) return null
        val variable = Identifier(curToken, curToken.literal)

        // Expect 'in'
        if (!expectPeek(TokenType.IN)) return null

        // Parse iterable expression
        nextToken()
        val iterable = parseExpression(LOWEST) ?: return null

        // Expect )
        if (!expectPeek(TokenType.RPAREN)) return null

        // Expect {
        if (!expectPeek(TokenType.LBRACE)) return null

        // Parse body
        val body = parseBlockStatement()

        return ForStatement(token, variable, iterable, body)
    }

    private fun parseTryStatement(): Statement? {
        val token = curToken
        if (!expectPeek(TokenType.LBRACE)) return null
        val body = parseBlockStatement()

        // Allow a newline between the try block's `}` and `catch`.
        while (peekTokenIs(TokenType.NEWLINE)) nextToken()
        if (!expectPeek(TokenType.CATCH)) return null

        var catchVar: Identifier? = null
        if (peekTokenIs(TokenType.LPAREN)) {
            nextToken() // cur = (
            if (!expectPeek(TokenType.IDENT)) return null
            catchVar = Identifier(curToken, curToken.literal)
            if (!expectPeek(TokenType.RPAREN)) return null
        }

        if (!expectPeek(TokenType.LBRACE)) return null
        val catch = parseBlockStatement()

        return TryStatement(token, body, catchVar, catch)
    }

    private fun parseWhileStatement(): WhileStatement? {
        val token = curToken

        // Expect (
        if (!expectPeek(TokenType.LPAREN)) return null

        // Parse condition
        nextToken()
        val condition = parseExpression(LOWEST) ?: return null

        // Expect )
        if (!expectPeek(TokenType.RPAREN)) return null

        // Expect {
        if (!expectPeek(TokenType.LBRACE)) return null

        // Parse body
        val body = parseBlockStatement()

        return WhileStatement(token, condition, body)
    }

    private fun parseVarDecl(): Statement? {
        val token = curToken

        // Destructuring: let [a, b] = expr  /  let {a, b} = expr
        if (peekTokenIs(TokenType.LBRACKET)) {
            return parseDestructure(token, TokenType.RBRACKET, isArray = true)
        }
        if (peekTokenIs(TokenType.LBRACE)) {
            return parseDestructure(token, TokenType.RBRACE, isArray = false)
        }

        if (!expectPeek(TokenType.IDENT)) return null
        val name = Identifier(curToken, curToken.literal)

        if (!expectPeek(TokenType.ASSIGN)) return null
        nextToken()

        val value = parseExpression(LOWEST) ?: return null

        return VarDecl(token, name, value)
    }

    private fun parseDestructure(token: Token, close: TokenType, isArray: Boolean): Statement? {
        nextToken() // cur = opening [ or {
        val names = mutableListOf<Identifier>()
        if (!peekTokenIs(close)) {
            if (!expectPeek(TokenType.IDENT)) return null
            names.add(Identifier(curToken, curToken.literal))
            while (peekTokenIs(TokenType.COMMA)) {
                nextToken()
                if (!expectPeek(TokenType.IDENT)) return null
                names.add(Identifier(curToken, curToken.literal))
            }
        }
        if (!expectPeek(close)) return null
        if (!expectPeek(TokenType.ASSIGN)) return null
        nextToken()
        val value = parseExpression(LOWEST) ?: return null
        return if (isArray) ArrayDestructure(token, names, value)
        else ObjectDestructure(token, names, value)
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
            nextToken() // cur = else
            if (peekTokenIs(TokenType.IF)) {
                // else if: parse the nested if and wrap it as the alternative block
                nextToken() // cur = if
                val nested = parseIfStatement() ?: return null
                alternative = BlockStatement(curToken, mutableListOf(nested))
            } else {
                if (!expectPeek(TokenType.LBRACE)) return null
                alternative = parseBlockStatement()
            }
        }

        return IfStatement(token, condition, consequence, alternative)
    }

    private fun parseBlockStatement(): BlockStatement {
        val block = BlockStatement(curToken)
        nextToken() // consume opening brace

        while (!curTokenIs(TokenType.RBRACE) && !curTokenIs(TokenType.EOF)) {
            consumeEndOfStatement()
            if (curTokenIs(TokenType.RBRACE)) break

            parseStatement()?.let { block.statements.add(it) }
            nextToken() // move past the statement
            consumeEndOfStatement()
        }

        // Note: we leave curToken on RBRACE, the caller should advance if needed
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

    private fun parseStringTemplate(): Expression? {
        val token = curToken
        val parts = mutableListOf<Expression>()
        val literal = curToken.literal
        var i = 0

        while (i < literal.length) {
            // Find next ${
            val start = i
            while (i < literal.length &&
                    !(i + 1 < literal.length && literal[i] == '$' && literal[i + 1] == '{')) {
                i++
            }

            // Add string part if non-empty
            if (i > start) {
                val strToken = Token(TokenType.STRING, literal.substring(start, i))
                parts.add(StringLiteral(strToken, literal.substring(start, i)))
            }

            // If we found ${, parse the expression
            if (i + 1 < literal.length && literal[i] == '$' && literal[i + 1] == '{') {
                i += 2 // skip ${

                // Find matching }
                var braceCount = 1
                val exprStart = i
                while (i < literal.length && braceCount > 0) {
                    if (literal[i] == '{') braceCount++ else if (literal[i] == '}') braceCount--
                    if (braceCount > 0) i++
                }

                // Extract and parse the expression
                val exprStr = literal.substring(exprStart, i)
                if (i < literal.length) i++ // skip closing }

                // Create a new lexer and parser for the expression
                val exprLexer = Lexer(exprStr)
                val exprParser = Parser(exprLexer)
                val expr = exprParser.parseExpression(LOWEST)

                if (exprParser.errors().isNotEmpty()) {
                    errors.addAll(exprParser.errors())
                }

                if (expr != null) {
                    parts.add(expr)
                }
            }
        }

        return StringTemplate(token, parts)
    }

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

    private fun parseArrayLiteral(): Expression? {
        val token = curToken
        val elements = parseExpressionList(TokenType.RBRACKET)
        return ArrayLiteral(token, elements)
    }

    private fun parseExpressionList(end: TokenType): List<Expression> {
        val list = mutableListOf<Expression>()

        if (peekTokenIs(end)) {
            nextToken()
            return list
        }

        nextToken()
        parseListElement()?.let { list.add(it) }

        while (peekTokenIs(TokenType.COMMA)) {
            nextToken()
            nextToken()
            parseListElement()?.let { list.add(it) }
        }

        if (!expectPeek(end)) {
            return emptyList()
        }

        return list
    }

    /** Parses one array/argument element, allowing a spread element (...expr). */
    private fun parseListElement(): Expression? {
        if (curTokenIs(TokenType.ELLIPSIS)) {
            val token = curToken
            nextToken()
            val value = parseExpression(LOWEST) ?: return null
            return SpreadExpr(token, value)
        }
        return parseExpression(LOWEST)
    }

    private fun parseObjectLiteral(): Expression? {
        val token = curToken
        val pairs = mutableMapOf<String, Expression>()

        if (peekTokenIs(TokenType.RBRACE)) {
            nextToken()
            return ObjectLiteral(token, pairs)
        }

        while (!peekTokenIs(TokenType.RBRACE)) {
            // Skip newlines before keys
            while (peekTokenIs(TokenType.NEWLINE)) {
                nextToken()
            }
            if (peekTokenIs(TokenType.RBRACE)) break

            nextToken()
            // Support both string "key" and identifier key
            val key =
                    if (curTokenIs(TokenType.STRING) || curTokenIs(TokenType.IDENT)) {
                        curToken.literal
                    } else {
                        addError("expected string or identifier as object key")
                        return null
                    }

            if (!expectPeek(TokenType.COLON)) {
                return null
            }

            nextToken()
            val value = parseExpression(LOWEST) ?: return null
            pairs[key] = value

            // Separator can be COMMA or NEWLINE
            if (!peekTokenIs(TokenType.RBRACE) &&
                            !peekTokenIs(TokenType.COMMA) &&
                            !peekTokenIs(TokenType.NEWLINE)
            ) {
                addError("expected comma, newline or }")
                return null
            }

            // Consume comma if present, newlines are skipped at start of loop
            if (peekTokenIs(TokenType.COMMA)) {
                nextToken()
            }
        }

        if (!expectPeek(TokenType.RBRACE)) {
            return null
        }

        return ObjectLiteral(token, pairs)
    }

    private fun parseIndexExpression(left: Expression): Expression? {
        val token = curToken
        nextToken()
        val index = parseExpression(LOWEST) ?: return null

        if (!expectPeek(TokenType.RBRACKET)) {
            return null
        }

        return IndexExpr(token, left, index)
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

    private fun parseTernaryExpression(condition: Expression): Expression? {
        val token = curToken
        nextToken() // move onto the consequent
        val consequent = parseExpression(LOWEST) ?: return null
        if (!expectPeek(TokenType.COLON)) return null
        nextToken() // move onto the alternative
        val alternative = parseExpression(LOWEST) ?: return null
        return TernaryExpr(token, condition, consequent, alternative)
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

    /**
     * Desugars `fn name(a, b) { ... }` into `let name = fn(a, b) { ... }`.
     * Binding the name in the enclosing scope is what makes recursion work.
     */
    private fun parseFunctionDeclaration(): Statement? {
        val fnToken = curToken
        if (!expectPeek(TokenType.IDENT)) return null
        val name = Identifier(curToken, curToken.literal)
        if (!expectPeek(TokenType.LPAREN)) return null
        val parameters = parseFunctionParameters() ?: return null
        if (!expectPeek(TokenType.LBRACE)) return null
        val body = parseBlockStatement()
        return VarDecl(fnToken, name, FunctionLiteral(fnToken, parameters, body))
    }

    private fun parseFunctionLiteral(): Expression? {
        val token = curToken
        if (!expectPeek(TokenType.LPAREN)) return null
        val parameters = parseFunctionParameters() ?: return null
        if (!expectPeek(TokenType.LBRACE)) return null
        val body = parseBlockStatement()
        return FunctionLiteral(token, parameters, body)
    }

    private fun parseFunctionParameters(): List<Identifier>? {
        val identifiers = mutableListOf<Identifier>()

        if (peekTokenIs(TokenType.RPAREN)) {
            nextToken()
            return identifiers
        }

        nextToken()
        identifiers.add(Identifier(curToken, curToken.literal))

        while (peekTokenIs(TokenType.COMMA)) {
            nextToken()
            nextToken()
            identifiers.add(Identifier(curToken, curToken.literal))
        }

        if (!expectPeek(TokenType.RPAREN)) return null

        return identifiers
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
        args.add(parseListElement() ?: return null)

        while (peekTokenIs(TokenType.COMMA)) {
            nextToken()
            nextToken()
            args.add(parseListElement() ?: return null)
        }

        if (!expectPeek(TokenType.RPAREN)) return null

        return args
    }

    companion object {
        private const val LOWEST = 1
        private const val TERNARY = 2
        private const val ELVIS = 3
        private const val OR = 4
        private const val AND = 5
        private const val EQUALS = 6
        private const val LESSGREATER = 7
        private const val SUM = 8
        private const val PRODUCT = 9
        private const val PREFIX = 10
        private const val CALL = 11
        private const val ACCESS = 12

        private val precedences =
                mapOf(
                        TokenType.QUESTION to TERNARY,
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
                        TokenType.PERCENT to PRODUCT,
                        TokenType.LPAREN to CALL,
                        TokenType.LBRACKET to ACCESS,
                        TokenType.DOT to ACCESS,
                        TokenType.SAFE_ACCESS to ACCESS
                )
    }
}
