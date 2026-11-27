package com.student.smartstudy

/**
 * Auto-indent for the Python Lab editor. When Enter is pressed, the new line gets the indentation Python expects:
 *
 *  - after a line ending in ':' (if, for, while, def, class, try, with ...) one level more
 *  - after return, pass, break, continue or raise one level less
 *  - after an opening bracket one level more
 *  - otherwise the same as the line above
 *
 * A ':' inside a string, a comment or brackets (dictionaries, slices) does not start a block.
 * Plain Kotlin, so it can be tested on a computer.
 */
internal object PythonAutoIndent {
    class Edit(val text: String, val cursor: Int)

    private const val LEVEL = "    "
    private val blockEnders = setOf("return", "pass", "break", "continue", "raise")

    /**
     * [old] had the selection [start]..[end]; the keyboard turned it into [new] with the cursor at [cursor].
     * Returns the indented text when that change was a single press of Enter, otherwise null (so typing, pasting
     * and deleting are left alone).
     */
    fun onEdit(old: String, start: Int, end: Int, new: String, cursor: Int): Edit? {
        if (start < 0 || end < start || end > old.length) return null
        if (cursor != start + 1 || new.length != old.length - (end - start) + 1) return null
        if (new[start] != '\n' || !new.regionMatches(0, old, 0, start) ||
            !new.regionMatches(cursor, old, end, old.length - end)) return null

        val before = new.substring(0, start)
        val line = before.substring(before.lastIndexOf('\n') + 1)
        var rest = new.substring(cursor)
        val indent: String
        if (line.isBlank()) {
            // Enter on an empty line, or inside the indentation: stay at the same depth
            indent = line
        } else {
            indent = indentAfter(before)
            rest = rest.trimStart(' ', '\t')      // text moved down starts exactly at the new indentation
        }
        if (indent.isEmpty() && rest.length == new.length - cursor) return null
        return Edit(before + "\n" + indent + rest, cursor + indent.length)
    }

    /** The indentation for the line that follows [code]. */
    fun indentAfter(code: String): String {
        var depth = 0
        var quote = ""              // the quote that opened the string we are in, or empty
        var logicalStart = 0        // where the current statement began (it may span several lines)
        var last = ' '              // last character of the statement that is not space or comment
        var continued = false       // the line ends with a backslash
        var i = 0
        while (i < code.length) {
            val c = code[i]
            if (quote.isNotEmpty()) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (code.startsWith(quote, i)) {
                    i += quote.length
                    last = c
                    quote = ""
                    continue
                }
                if (c == '\n' && quote.length == 1) quote = ""       // unfinished string
                else {
                    i++
                    continue
                }
            }
            when (c) {
                '#' -> {
                    while (i < code.length && code[i] != '\n') i++
                    continue
                }
                '"', '\'' -> {
                    val triple = "$c$c$c"
                    quote = if (code.startsWith(triple, i)) triple else c.toString()
                    last = c
                    i += quote.length
                    continue
                }
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth > 0) depth--
                '\n' -> {
                    if (depth == 0 && !continued) {
                        logicalStart = i + 1
                        last = ' '
                    }
                    continued = false
                }
            }
            if (c == '\\' && (i + 1 == code.length || code[i + 1] == '\n')) continued = true
            else if (!c.isWhitespace()) last = c
            i++
        }

        val lineStart = code.lastIndexOf('\n') + 1
        val lineIndent = leading(code, lineStart)
        // inside a long string the text is the user's own: keep the line's indentation
        if (quote.length == 3) return lineIndent
        if (depth > 0) return if (last == '(' || last == '[' || last == '{') deeper(lineIndent) else lineIndent
        if (continued) return if (lineStart == logicalStart) deeper(lineIndent) else lineIndent
        val base = leading(code, logicalStart)
        if (last == ':') return deeper(base)
        val firstWord = code.substring(logicalStart + base.length).takeWhile { it.isLetterOrDigit() || it == '_' }
        return if (firstWord in blockEnders) shallower(base) else base
    }

    private fun leading(code: String, from: Int): String {
        var j = from
        while (j < code.length && (code[j] == ' ' || code[j] == '\t')) j++
        return code.substring(from, j)
    }

    private fun deeper(indent: String) = indent + if ('\t' in indent) "\t" else LEVEL

    private fun shallower(indent: String) =
        if (indent.endsWith("\t")) indent.dropLast(1) else indent.dropLast(minOf(LEVEL.length, indent.length))
}
