package com.nofilter.ai.engine

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.*

/** On-device math / logic / unit-conversion solver (no network, no libraries). */
object MathSolver {

    fun trySolve(rawQuery: String): String? {
        val q = rawQuery.trim().trimEnd('?', '.', '!')
        val lower = q.lowercase()

        // "what is X% of Y"
        Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:of|percent of)\\s*(\\d+(?:\\.\\d+)?)").find(lower)?.let { m ->
            val x = m.groupValues[1].toDouble(); val y = m.groupValues[2].toDouble()
            return "🧮 **${fmt(x)}% of ${fmt(y)} = ${fmt(x / 100.0 * y)}**"
        }
        // "X% of Y" without words
        if (Regex("^\\d+(?:\\.\\d+)?\\s*%\\s+\\d").containsMatchIn(lower)) {
            Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s+(\\d+(?:\\.\\d+)?)").find(lower)?.let { m ->
                val x = m.groupValues[1].toDouble(); val y = m.groupValues[2].toDouble()
                return "🧮 **${fmt(x)}% of ${fmt(y)} = ${fmt(x / 100.0 * y)}**"
            }
        }
        // unit conversion
        val conv = Regex("convert\\s+(\\d+(?:\\.\\d+)?)\\s*([a-z°]+)\\s+(?:to|in|into)\\s*([a-z°]+)", RegexOption.IGNORE_CASE).find(q)
            ?: Regex("(\\d+(?:\\.\\d+)?)\\s*([a-z°]+)\\s+(?:to|in)\\s+([a-z°]+)", RegexOption.IGNORE_CASE).find(q)
        if (conv != null) {
            val v = conv.groupValues[1].toDouble()
            val from = conv.groupValues[2].lowercase()
            val to = conv.groupValues[3].lowercase()
            val r = convert(v, from, to)
            if (r != null) return "🧮 **${fmt(v)} $from = ${fmt(r)} $to**"
        }
        // date offset: "N days from today"
        Regex("(\\d+)\\s*days?\\s+(?:from|after)\\s+(today|now)").find(lower)?.let { m ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, m.groupValues[1].toInt())
            return "📅 **${m.groupValues[1].toInt()} days from today = ${SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(cal.time)}**"
        }
        // pure expression
        val cleaned = lower
            .replace(Regex("^(please\\s+)?(calculate|compute|solve|what is|whats|eval|evaluates?)\\s*"), "")
            .replace(Regex("^(=|equals?)\\s*"), "")
            .trim()
        if (cleaned.length >= 3 && cleaned.matches(Regex("[0-9+\\-*/^().,%\\s]+")) && cleaned.any { it.isDigit() } && cleaned.any { it in "+-*/^" }) {
            return try {
                val v = Parser(cleaned.replace(" ", "")).parse()
                "🧮 **${fmt(v)}**"
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "undefined"
        return if (v == floor(v) && abs(v) < 1e15) v.toLong().toString()
        else {
            var s = String.format(Locale.US, "%.10f", v).trimEnd('0').trimEnd('.')
            if (s.length > 18) s = String.format(Locale.US, "%.6g", v)
            s
        }
    }

    private val UNITS = mapOf(
        "km" to 1000.0, "m" to 1.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.344, "ft" to 0.3048, "in" to 0.0254, "yd" to 0.9144,
        "kg" to 1.0, "g" to 0.001, "mg" to 1e-6, "lb" to 0.45359237, "oz" to 0.028349523125,
        "l" to 1.0, "ml" to 0.001, "gal" to 3.785411784,
        "b" to 1.0, "kb" to 1e3, "mb" to 1e6, "gb" to 1e9, "tb" to 1e12,
        "c" to -1.0, "f" to -2.0, "k" to -3.0
    )
    private val UNIT_GROUP = mapOf(
        "km" to "len", "m" to "len", "cm" to "len", "mm" to "len", "mi" to "len", "ft" to "len", "in" to "len", "yd" to "len",
        "kg" to "mass", "g" to "mass", "mg" to "mass", "lb" to "mass", "oz" to "mass",
        "l" to "vol", "ml" to "vol", "gal" to "vol",
        "b" to "data", "kb" to "data", "mb" to "data", "gb" to "data", "tb" to "data",
        "c" to "temp", "f" to "temp", "k" to "temp"
    )

    private fun convert(v: Double, from: String, to: String): Double? {
        val f = from.trimEnd('°'); val t = to.trimEnd('°')
        if (UNIT_GROUP[f] != UNIT_GROUP[t]) return null
        return when (UNIT_GROUP[f]) {
            "temp" -> {
                val celsius = when (f) { "c" -> v; "f" -> (v - 32) * 5 / 9; "k" -> v - 273.15; else -> return null }
                when (t) { "c" -> celsius; "f" -> celsius * 9 / 5 + 32; "k" -> celsius + 273.15; else -> return null }
            }
            else -> {
                val base = UNITS[f] ?: return null
                val target = UNITS[t] ?: return null
                v * base / target
            }
        }
    }

    /** Recursive-descent expression parser. Supports + - * / % ^ ( ) functions and constants. */
    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = parseExpr()
            skipWs()
            if (pos < s.length) throw IllegalArgumentException("trailing input at $pos")
            return v
        }

        private fun skipWs() { while (pos < s.length && s[pos] == ' ') pos++ }

        private fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                skipWs()
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                    val op = s[pos++]
                    val r = parseTerm()
                    v = if (op == '+') v + r else v - r
                } else return v
            }
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                skipWs()
                if (pos < s.length && (s[pos] == '*' || s[pos] == '/' || s[pos] == '%')) {
                    val op = s[pos++]
                    val r = parseFactor()
                    v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
                } else return v
            }
        }

        private fun parseFactor(): Double {
            skipWs()
            var v = parseUnary()
            skipWs()
            if (pos < s.length && s[pos] == '^') {
                pos++
                v = v.pow(parseFactor())
            }
            // percent postfix
            skipWs()
            if (pos < s.length && s[pos] == '%') {
                pos++
                v = v / 100.0
            }
            return v
        }

        private fun parseUnary(): Double {
            skipWs()
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            skipWs()
            if (pos >= s.length) throw IllegalArgumentException("unexpected end")
            val ch = s[pos]
            if (ch == '(') {
                pos++
                val v = parseExpr()
                skipWs()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("missing )")
                pos++
                return v
            }
            if (ch.isDigit() || ch == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDouble()
            }
            if (ch.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val name = s.substring(start, pos)
                when (name) {
                    "pi" -> return PI
                    "e" -> return E
                    "tau" -> return 2 * PI
                }
                skipWs()
                if (pos < s.length && s[pos] == '(') {
                    pos++
                    val arg = parseExpr()
                    skipWs()
                    if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException("missing )")
                    pos++
                    return when (name) {
                        "sqrt" -> sqrt(arg)
                        "sin" -> sin(arg)
                        "cos" -> cos(arg)
                        "tan" -> tan(arg)
                        "asin" -> asin(arg)
                        "acos" -> acos(arg)
                        "atan" -> atan(arg)
                        "log" -> log10(arg)
                        "ln" -> ln(arg)
                        "log2" -> (ln(arg) / ln(2.0))
                        "abs" -> abs(arg)
                        "exp" -> exp(arg)
                        "floor" -> floor(arg)
                        "ceil" -> ceil(arg)
                        "round" -> round(arg)
                        else -> throw IllegalArgumentException("unknown func $name")
                    }
                }
                throw IllegalArgumentException("unknown identifier $name")
            }
            throw IllegalArgumentException("unexpected char $ch")
        }
    }
}
