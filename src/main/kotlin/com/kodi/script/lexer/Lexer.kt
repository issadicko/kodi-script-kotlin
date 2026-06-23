package com.kodi.script.lexer

import com.kodi.script.token.Token
import com.kodi.script.token.TokenType

/** Lexer tokenizes KodiScript source code. */
class Lexer(private val input: String) {
    private var position: Int = 0
    private var readPosition: Int = 0
    private var ch: Char = '\u0000'
    private var line: Int = 1
    private var column: Int = 0
    private var prevToken: Token = Token(TokenType.ILLEGAL, "", 0, 0)

    init {
        readChar()
    }

    private fun readChar() {
        ch = if (readPosition >= input.length) '\u0000' else input[readPosition]
        position = readPosition
        readPosition++
        column++
    }

    private fun peekChar(): Char =
            if (readPosition >= input.length) '\u0000' else input[readPosition]

    fun nextToken(): Token {
        skipWhitespace()

        // Column of the first character of the token, so multi-character tokens
        // (identifiers, numbers, strings) report their start position — matching
        // the Go implementation rather than the position after consumption.
        val startColumn = column

        val tok: Token =
                when (ch) {
                    '=' ->
                            if (peekChar() == '=') {
                                readChar()
                                Token(TokenType.EQ, "==", line, column - 1)
                            } else {
                                newToken(TokenType.ASSIGN)
                            }
                    '+' ->
                            when (peekChar()) {
                                '+' -> {
                                    readChar()
                                    Token(TokenType.PLUS_PLUS, "++", line, column - 1)
                                }
                                '=' -> {
                                    readChar()
                                    Token(TokenType.PLUS_EQ, "+=", line, column - 1)
                                }
                                else -> newToken(TokenType.PLUS)
                            }
                    '-' ->
                            when (peekChar()) {
                                '-' -> {
                                    readChar()
                                    Token(TokenType.MINUS_MINUS, "--", line, column - 1)
                                }
                                '=' -> {
                                    readChar()
                                    Token(TokenType.MINUS_EQ, "-=", line, column - 1)
                                }
                                else -> newToken(TokenType.MINUS)
                            }
                    '*' ->
                            if (peekChar() == '=') {
                                readChar()
                                Token(TokenType.ASTERISK_EQ, "*=", line, column - 1)
                            } else {
                                newToken(TokenType.ASTERISK)
                            }
                    '/' ->
                            when (peekChar()) {
                                '/' -> {
                                    skipLineComment()
                                    return nextToken()
                                }
                                '*' -> {
                                    skipBlockComment()
                                    return nextToken()
                                }
                                '=' -> {
                                    readChar()
                                    Token(TokenType.SLASH_EQ, "/=", line, column - 1)
                                }
                                else -> newToken(TokenType.SLASH)
                            }
                    '%' -> newToken(TokenType.PERCENT)
                    '!' ->
                            if (peekChar() == '=') {
                                readChar()
                                Token(TokenType.NOT_EQ, "!=", line, column - 1)
                            } else {
                                newToken(TokenType.NOT)
                            }
                    '<' ->
                            if (peekChar() == '=') {
                                readChar()
                                Token(TokenType.LT_EQ, "<=", line, column - 1)
                            } else {
                                newToken(TokenType.LT)
                            }
                    '>' ->
                            if (peekChar() == '=') {
                                readChar()
                                Token(TokenType.GT_EQ, ">=", line, column - 1)
                            } else {
                                newToken(TokenType.GT)
                            }
                    '&' ->
                            if (peekChar() == '&') {
                                readChar()
                                Token(TokenType.AND, "&&", line, column - 1)
                            } else {
                                newToken(TokenType.ILLEGAL)
                            }
                    '|' ->
                            if (peekChar() == '|') {
                                readChar()
                                Token(TokenType.OR, "||", line, column - 1)
                            } else {
                                newToken(TokenType.ILLEGAL)
                            }
                    '?' ->
                            when (peekChar()) {
                                '.' -> {
                                    readChar()
                                    Token(TokenType.SAFE_ACCESS, "?.", line, column - 1)
                                }
                                ':' -> {
                                    readChar()
                                    Token(TokenType.ELVIS, "?:", line, column - 1)
                                }
                                else -> newToken(TokenType.QUESTION)
                            }
                    ',' -> newToken(TokenType.COMMA)
                    ';' -> newToken(TokenType.SEMICOLON)
                    ':' -> newToken(TokenType.COLON)
                    '(' -> newToken(TokenType.LPAREN)
                    ')' -> newToken(TokenType.RPAREN)
                    '{' -> newToken(TokenType.LBRACE)
                    '}' -> newToken(TokenType.RBRACE)
                    '[' -> newToken(TokenType.LBRACKET)
                    ']' -> newToken(TokenType.RBRACKET)
                    '.' ->
                            if (peekChar() == '.') {
                                readChar() // consume 2nd '.'
                                if (peekChar() == '.') {
                                    readChar() // consume 3rd '.'
                                    Token(TokenType.ELLIPSIS, "...", line, column - 2)
                                } else {
                                    newToken(TokenType.ILLEGAL)
                                }
                            } else {
                                newToken(TokenType.DOT)
                            }
                    '"', '\'', '`' -> {
                        val (str, isTemplate) = readString(ch)
                        val type = if (isTemplate) TokenType.STRING_TEMPLATE else TokenType.STRING
                        Token(type, str, line, startColumn)
                    }
                    '\n' -> {
                        val result =
                                if (prevToken.type.canEndStatement()) {
                                    Token(TokenType.NEWLINE, "\\n", line, column)
                                } else {
                                    line++
                                    column = 0
                                    readChar()
                                    return nextToken()
                                }
                        line++
                        column = 0
                        result
                    }
                    '\u0000' -> Token(TokenType.EOF, "", line, column)
                    else -> {
                        when {
                            ch.isLetter() || ch == '_' -> {
                                val ident = readIdentifier()
                                val type = Token.lookupIdent(ident)
                                val token = Token(type, ident, line, startColumn)
                                prevToken = token
                                return token
                            }
                            ch.isDigit() -> {
                                val num = readNumber()
                                val token = Token(TokenType.NUMBER, num, line, startColumn)
                                prevToken = token
                                return token
                            }
                            else -> newToken(TokenType.ILLEGAL)
                        }
                    }
                }

        readChar()
        prevToken = tok
        return tok
    }

    private fun newToken(type: TokenType): Token = Token(type, ch.toString(), line, column)

    private fun skipWhitespace() {
        while (ch == ' ' || ch == '\t' || ch == '\r') {
            readChar()
        }
    }

    private fun skipLineComment() {
        while (ch != '\n' && ch != '\u0000') {
            readChar()
        }
    }

    /** Skips a block comment. On entry ch is '/', peek is '*'. */
    private fun skipBlockComment() {
        readChar() // consume '/'
        readChar() // consume '*'
        while (ch != '\u0000') {
            if (ch == '*' && peekChar() == '/') {
                readChar() // consume '*'
                readChar() // consume '/'
                return
            }
            if (ch == '\n') {
                line++
                column = 0
            }
            readChar()
        }
    }

    private fun readIdentifier(): String {
        val startPos = position
        while (ch.isLetterOrDigit() || ch == '_') {
            readChar()
        }
        return input.substring(startPos, position)
    }

    private fun readNumber(): String {
        val startPos = position
        while (ch.isDigit()) {
            readChar()
        }
        if (ch == '.' && peekChar().isDigit()) {
            readChar() // consume '.'
            while (ch.isDigit()) {
                readChar()
            }
        }
        return input.substring(startPos, position)
    }

    private fun readString(quote: Char): Pair<String, Boolean> {
        val result = StringBuilder()
        var isTemplate = false
        readChar() // skip opening quote
        while (ch != quote && ch != '\u0000') {
            if (ch == '\\') {
                readChar()
                when (ch) {
                    'n' -> result.append('\n')
                    't' -> result.append('\t')
                    '"' -> result.append('"')
                    '\'' -> result.append('\'')
                    '`' -> result.append('`')
                    '\\' -> result.append('\\')
                    '$' -> result.append('$')
                    else -> result.append(ch)
                }
            } else if (ch == '$' && peekChar() == '{') {
                // Template expression detected
                isTemplate = true
                result.append('$')
                result.append('{')
                readChar() // consume $
            } else if (ch == '\n') {
                result.append('\n')
                line++
                column = 0
            } else {
                result.append(ch)
            }
            readChar()
        }
        return Pair(result.toString(), isTemplate)
    }
}
