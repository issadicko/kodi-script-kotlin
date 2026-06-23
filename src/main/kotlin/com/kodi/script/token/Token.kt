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
        STRING_TEMPLATE,

        // Operators
        ASSIGN, // =
        PLUS, // +
        MINUS, // -
        ASTERISK, // *
        SLASH, // /
        PERCENT, // %

        // Compound assignment and increment/decrement
        PLUS_EQ, // +=
        MINUS_EQ, // -=
        ASTERISK_EQ, // *=
        SLASH_EQ, // /=
        PLUS_PLUS, // ++
        MINUS_MINUS, // --

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
        QUESTION, // ? (ternary)

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
        ELLIPSIS, // ... (spread / rest)

        // Keywords
        LET,
        IF,
        ELSE,
        TRUE,
        FALSE,
        NULL,
        RETURN,
        FOR,
        IN,
        FN,
        WHILE,
        BREAK,
        CONTINUE,
        TRY,
        CATCH;

        /** Returns true if this token type can end a statement (for ASI). */
        fun canEndStatement(): Boolean = this in STATEMENT_ENDERS

        companion object {
                /**
                 * Token types that can terminate a statement (for Automatic Semicolon
                 * Insertion). Stored as a static EnumSet so the lexer's per-newline
                 * check is allocation-free.
                 */
                private val STATEMENT_ENDERS =
                        java.util.EnumSet.of(
                                IDENT,
                                NUMBER,
                                STRING,
                                STRING_TEMPLATE,
                                TRUE,
                                FALSE,
                                NULL,
                                RPAREN,
                                RBRACE,
                                RBRACKET,
                                PLUS_PLUS,
                                MINUS_MINUS,
                                BREAK,
                                CONTINUE
                        )
        }
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
                                "in" to TokenType.IN,
                                "fn" to TokenType.FN,
                                "while" to TokenType.WHILE,
                                "break" to TokenType.BREAK,
                                "continue" to TokenType.CONTINUE,
                                "try" to TokenType.TRY,
                                "catch" to TokenType.CATCH
                        )

                /** Looks up an identifier to check if it's a keyword. */
                fun lookupIdent(ident: String): TokenType = keywords[ident] ?: TokenType.IDENT
        }
}
