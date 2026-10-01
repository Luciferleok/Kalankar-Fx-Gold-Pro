package com.example.livegoldai.data

/**
 * Tiny dependency-free JSON reader/writer used only by the Prediction Ledger file.
 * Supports objects, arrays, strings, numbers, booleans and null.
 */
object MiniJson {

    fun write(value: Any?): String {
        val sb = StringBuilder()
        writeTo(sb, value)
        return sb.toString()
    }

    private fun writeTo(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> writeString(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long -> sb.append(v.toString())
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Float -> writeTo(sb, v.toDouble())
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    writeTo(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    writeTo(sb, item)
                }
                sb.append(']')
            }
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    /** Returns Map<String, Any?>, List<Any?>, String, Double, Boolean or null. Throws on malformed input. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.readValue()
        p.skipWs()
        if (p.pos != text.length) throw IllegalArgumentException("Trailing data at ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWs()
            if (pos >= s.length) throw IllegalArgumentException("Unexpected end")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> if (c == '-' || c.isDigit()) readNumber() else throw IllegalArgumentException("Bad char '$c' at $pos")
            }
        }

        private fun expect(word: String) {
            if (!s.startsWith(word, pos)) throw IllegalArgumentException("Expected $word at $pos")
            pos += word.length
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return map }
            while (true) {
                skipWs()
                val key = readString()
                skipWs()
                if (pos >= s.length || s[pos] != ':') throw IllegalArgumentException("Expected : at $pos")
                pos++
                map[key] = readValue()
                skipWs()
                if (pos >= s.length) throw IllegalArgumentException("Unclosed object")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> throw IllegalArgumentException("Expected , or } at $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return list }
            while (true) {
                list.add(readValue())
                skipWs()
                if (pos >= s.length) throw IllegalArgumentException("Unclosed array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> throw IllegalArgumentException("Expected , or ] at $pos")
                }
            }
        }

        private fun readString(): String {
            if (s[pos] != '"') throw IllegalArgumentException("Expected string at $pos")
            pos++
            val sb = StringBuilder()
            while (pos < s.length) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) break
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                val hex = s.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unclosed string")
        }

        private fun readNumber(): Double {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' || s[pos] == '+' || s[pos] == '-')) pos++
            return s.substring(start, pos).toDouble()
        }
    }
}

// Small typed accessors for parsed maps
internal fun Map<String, Any?>.str(key: String, def: String = ""): String = (this[key] as? String) ?: def
internal fun Map<String, Any?>.num(key: String, def: Double = 0.0): Double = (this[key] as? Number)?.toDouble() ?: def
internal fun Map<String, Any?>.long(key: String, def: Long = 0L): Long = (this[key] as? Number)?.toLong() ?: def
internal fun Map<String, Any?>.int(key: String, def: Int = 0): Int = (this[key] as? Number)?.toInt() ?: def
internal fun Map<String, Any?>.bool(key: String, def: Boolean = false): Boolean = (this[key] as? Boolean) ?: def
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.obj(key: String): Map<String, Any?> = (this[key] as? Map<String, Any?>) ?: emptyMap()
@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.list(key: String): List<Any?> = (this[key] as? List<Any?>) ?: emptyList()
