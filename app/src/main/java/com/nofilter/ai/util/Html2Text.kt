package com.nofilter.ai.util

import java.util.regex.Pattern

/** Tiny HTML -> plain text converter (no external deps). */
object Html2Text {

    private val SCRIPT_STYLE = Pattern.compile("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>")
    private val COMMENTS = Pattern.compile("(?s)<!--.*?-->")
    private val BLOCK = Pattern.compile("(?is)</?(p|div|br|li|tr|h[1-6]|section|article|header|footer|ul|ol|table|blockquote)[^>]*>")
    private val TAGS = Pattern.compile("(?s)<[^>]+>")
    private val WS = Pattern.compile("[ \\t\\x0B\\f\\r]+")
    private val NL = Pattern.compile("\\n{3,}")

    fun toText(html: String): String {
        var s = html
        s = SCRIPT_STYLE.matcher(s).replaceAll(" ")
        s = COMMENTS.matcher(s).replaceAll(" ")
        s = s.replace(Regex("(?is)<br\\s*/?>"), "\n")
        s = BLOCK.matcher(s).replaceAll("\n")
        s = s.replace(Regex("(?is)</li>"), "\n")
        s = TAGS.matcher(s).replaceAll("")
        s = decodeEntities(s)
        s = WS.matcher(s).replaceAll(" ")
        s = NL.matcher(s).replaceAll("\n\n")
        return s.trim()
    }

    fun decodeEntities(s: String): String {
        var out = s
        val named = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
            "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
            "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "copy" to "©", "reg" to "®"
        )
        for ((k, v) in named) {
            out = out.replace("&$k;", v)
        }
        out = out.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            val code = m.groupValues[1].toInt(16)
            if (code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        }
        out = out.replace(Regex("&#(\\d+);")) { m ->
            val code = m.groupValues[1].toInt()
            if (code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        }
        return out
    }

    /** Extract the most query-relevant sentences from a text. */
    fun relevantSentences(text: String, query: String, maxSentences: Int = 5, maxChars: Int = 1000): String {
        val qTokens = Text.tokens(query)
        if (qTokens.isEmpty()) return text.take(600)
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.length > 25 && it.length < 400 }
        val scored = sentences.map { s ->
            val st = Text.tokens(s)
            val hit = qTokens.count { st.contains(it) }
            s to hit.toDouble() / (1 + st.size * 0.02)
        }.sortedByDescending { it.second }
        val picked = scored.take(maxSentences).sortedBy { sentences.indexOf(it.first) }.map { it.first }
        var result = picked.joinToString(" ")
        if (result.length > maxChars) result = result.take(maxChars)
        if (result.isBlank()) result = text.take(maxChars)
        return result
    }
}
