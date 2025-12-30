package com.kodi.script.token

/** Represents token types in KodiScript. */
enum class TokenType {
        // Special
        ILLEGAL,
        EOF,
        NEWLINE,

        // Identifiers and literals
        IDENT,
        NUMBER,
        STRING,

        // Operators
        ASSIGN, // =
        PLUS, // +
        MINUS, // -
        ASTERISK, // *
        SLASH, // /

        // Comparison
        EQ, // ==
        NOT_EQ, // !=
        LT, // <
        GT, // >
        LT_EQ, // <=
        GT_EQ, // >=

        // Logical
        AND, // &&
        OR, // ||
        NOT, // !

        // Null-safety
        SAFE_ACCESS, // ?.
        ELVIS, // ?:

        // Delimiters
        COMMA, // ,
        SEMICOLON, // ;
        COLON, // :
        LPAREN, // (
        RPAREN, // )
        LBRACE, // {
        RBRACE, // }
        LBRACKET, // [
        RBRACKET, // ]
        DOT, // .

        // Keywords
        LET,
        IF,
        ELSE,
        TRUE,
        FALSE,
        NULL,
        RETURN,
        FOR,
        IN;

        /** Returns true if this token type can end a statement (for ASI). */
        fun canEndStatement(): Boolean =
                this in setOf(IDENT, NUMBER, STRING, TRUE, FALSE, NULL, RPAREN, RBRACE)

        /** Returns true if this token type indicates statement continuation. */
        fun isOperatorContinuation(): Boolean =
                this in
                        setOf(
                                PLUS,
                                MINUS,
                                ASTERISK,
                                SLASH,
                                AND,
                                OR,
                                EQ,
                                NOT_EQ,
                                LT,
                                GT,
                                LT_EQ,
                                GT_EQ,
                                SAFE_ACCESS,
                                ELVIS,
                                DOT,
                                COMMA
                        )
}

/** Represents a single token with type, value, and position. */
data class Token(val type: TokenType, val literal: String, val line: Int = 1, val column: Int = 1) {
        companion object {
                private val keywords =
                        mapOf(
                                "let" to TokenType.LET,
                                "if" to TokenType.IF,
                                "else" to TokenType.ELSE,
                                "true" to TokenType.TRUE,
                                "false" to TokenType.FALSE,
                                "null" to TokenType.NULL,
                                "return" to TokenType.RETURN,
                                "for" to TokenType.FOR,
                                "in" to TokenType.IN
                        )

                /** Looks up an identifier to check if it's a keyword. */
                fun lookupIdent(ident: String): TokenType = keywords[ident] ?: TokenType.IDENT
        }
}
