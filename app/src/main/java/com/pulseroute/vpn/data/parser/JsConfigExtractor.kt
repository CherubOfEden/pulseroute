package com.pulseroute.vpn.data.parser

/**
 * Extracts the first complete JSON value from a JavaScript-shaped payload.
 *
 * Supported shapes:
 *   const SERVERS = [ ... ];
 *   window.servers = { "servers": [ ... ] };
 *   module.exports = [ ... ];
 *   jsonpCallback({ ... });
 *   [ ... ]        (plain JSON)
 *   { ... }        (plain JSON)
 */
internal object JsConfigExtractor {

    fun extract(input: String): String {
        val withoutBom = input.removePrefix("\uFEFF").trim()
        if (withoutBom.isEmpty()) throw ServerParseException("The payload is empty")

        val withoutComments = stripComments(withoutBom)
        val start = withoutComments.indexOfFirst { it == '[' || it == '{' }
        if (start < 0) throw ServerParseException("No JSON array or object found")

        val candidate = extractBalanced(withoutComments, start)
        return removeTrailingCommas(candidate)
    }

    private fun stripComments(input: String): String {
        val output = StringBuilder(input.length)
        var index = 0
        var quote: Char? = null
        var escaped = false

        while (index < input.length) {
            val char = input[index]
            if (quote != null) {
                output.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                index++
                continue
            }

            when {
                char == '"' || char == '\'' -> {
                    quote = char
                    output.append(char)
                    index++
                }

                char == '/' && index + 1 < input.length && input[index + 1] == '/' -> {
                    index += 2
                    while (index < input.length && input[index] != '\n') index++
                }

                char == '/' && index + 1 < input.length && input[index + 1] == '*' -> {
                    index += 2
                    while (index + 1 < input.length && !(input[index] == '*' && input[index + 1] == '/')) {
                        index++
                    }
                    index = (index + 2).coerceAtMost(input.length)
                }

                else -> {
                    output.append(char)
                    index++
                }
            }
        }
        return output.toString()
    }

    private fun extractBalanced(input: String, start: Int): String {
        val expectedClosers = ArrayDeque<Char>()
        var index = start
        var inString = false
        var escaped = false

        while (index < input.length) {
            val char = input[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                index++
                continue
            }

            when (char) {
                '"' -> inString = true
                '[' -> expectedClosers.addLast(']')
                '{' -> expectedClosers.addLast('}')
                ']', '}' -> {
                    val expected = expectedClosers.removeLastOrNull()
                        ?: throw ServerParseException("Unexpected '$char' at offset $index")
                    if (expected != char) {
                        throw ServerParseException("Mismatched '$char' at offset $index")
                    }
                    if (expectedClosers.isEmpty()) {
                        return input.substring(start, index + 1)
                    }
                }
            }
            index++
        }

        throw ServerParseException("Unterminated JSON value")
    }

    private fun removeTrailingCommas(input: String): String {
        val output = StringBuilder(input.length)
        var inString = false
        var escaped = false

        for (index in input.indices) {
            val char = input[index]
            if (inString) {
                output.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }

            if (char == '"') {
                inString = true
                output.append(char)
                continue
            }

            if (char == ',') {
                var lookahead = index + 1
                while (lookahead < input.length && input[lookahead].isWhitespace()) lookahead++
                if (lookahead < input.length && (input[lookahead] == '}' || input[lookahead] == ']')) {
                    continue
                }
            }
            output.append(char)
        }
        return output.toString()
    }
}
