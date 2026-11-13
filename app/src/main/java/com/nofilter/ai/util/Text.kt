package com.nofilter.ai.util

object Text {
    private val STOP = setOf(
        "a","an","the","is","are","was","were","be","been","being","am","do","does","did",
        "what","which","who","whom","whose","when","where","why","how","i","me","my","we","our",
        "you","your","he","she","it","they","them","their","this","that","these","those","of","to",
        "in","on","at","for","with","about","as","by","from","and","or","but","not","so","if","then",
        "can","could","will","would","should","shall","may","might","there","here","please","tell","give",
        "explain","know","want","get","make","say","mean","means","vs","vs."
    )

    fun tokens(s: String): List<String> {
        return s.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOP && !it.all { c -> c.isDigit() } }
    }

    /** overlap score between query tokens and target tokens */
    fun overlap(queryTokens: List<String>, targetTokens: List<String>): Double {
        if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
        val set = targetTokens.toSet()
        val hits = queryTokens.count { it in set }
        return hits.toDouble() / queryTokens.size
    }

    fun clamp(s: String, max: Int): String = if (s.length <= max) s else s.take(max) + "…"
}
