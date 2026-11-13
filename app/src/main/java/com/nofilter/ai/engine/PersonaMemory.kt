package com.nofilter.ai.engine

import com.nofilter.ai.db.Db
import com.nofilter.ai.db.MemoryRow
import com.nofilter.ai.db.PersonaRow
import com.nofilter.ai.util.Text

/**
 * Applies trainable personas ("how to behave") and persistent long-term memory,
 * including automatic memory extraction when the user teaches the AI a fact.
 */
object PersonaMemory {

    private val EXPLICIT_MEM = listOf(
        Regex("^(?:please\\s+)?(?:remember|memorize|don'?t forget)(?:\\s+that)?\\s*:?\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^/remember\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    private val IMPLICIT_MEM = listOf(
        Regex("\\b(my name is\\s+[A-Za-z][A-Za-z0-9 _-]{1,40})", RegexOption.IGNORE_CASE),
        Regex("\\b(i live in\\s+[A-Za-z][A-Za-z0-9 ,_-]{1,50})", RegexOption.IGNORE_CASE),
        Regex("\\b(i work as\\s+[^.!\\n]{3,60})", RegexOption.IGNORE_CASE),
        Regex("\\b(my favorite\\s+[^.!\\n]{3,60})", RegexOption.IGNORE_CASE),
        Regex("\\b(i prefer\\s+[^.!\\n]{3,70})", RegexOption.IGNORE_CASE)
    )

    /**
     * Checks if the user is teaching a fact. Returns (savedFact, explicitCommandOnly).
     */
    fun tryLearnFact(db: Db, query: String): Pair<String, Boolean>? {
        val q = query.trim()
        for (re in EXPLICIT_MEM) {
            val m = re.find(q)
            if (m != null) {
                val fact = m.groupValues[1].trim().trimEnd('.')
                if (fact.length >= 3 && db.memories().none { it.text.equals(fact, ignoreCase = true) }) {
                    db.addMemory(fact, "explicit")
                }
                return fact to true
            }
        }
        for (re in IMPLICIT_MEM) {
            val m = re.find(q)
            if (m != null) {
                val fact = m.groupValues[1].trim()
                if (fact.length >= 5 && db.memories().none { it.text.equals(fact, ignoreCase = true) }) {
                    db.addMemory(fact, "auto")
                    return fact to false
                }
            }
        }
        return null
    }

    fun matchMemories(db: Db, query: String, topK: Int = 3): List<MemoryRow> {
        val memories = db.memories()
        if (memories.isEmpty()) return emptyList()
        val ql = query.lowercase()
        // If user asks "what do you remember / know about me" or "what is my name"
        if (Regex("\\b(what do you (remember|know)|who am i|what('?s| is) my name|/memories)\\b").containsMatchIn(ql)) {
            val top = memories.take(8)
            for (m in top) db.bumpMemory(m.id)
            return top
        }
        val qt = Text.tokens(query)
        if (qt.isEmpty()) return emptyList()
        val matched = memories.map { m ->
            val mt = Text.tokens(m.text + " " + m.tags)
            m to (Text.overlap(qt, mt) + if (m.tags.isNotBlank() && ql.contains(m.tags.lowercase())) 0.3 else 0.0)
        }.filter { it.second > 0.12 }.sortedByDescending { it.second }.take(topK).map { it.first }
        for (m in matched) db.bumpMemory(m.id)
        return matched
    }

    fun contextBlock(persona: PersonaRow?, memories: List<MemoryRow>): String {
        val sb = StringBuilder()
        if (persona != null) {
            sb.append("Persona: ${persona.name} (${persona.emoji}). Tone: ${persona.tone}.\n")
            if (persona.instructions.isNotBlank()) {
                sb.append("Behavior instructions from the user: ${persona.instructions}\n")
            }
        }
        if (memories.isNotEmpty()) {
            sb.append("Things to remember about the user:\n")
            for (m in memories) sb.append("- ${m.text}\n")
        }
        return sb.toString().trim()
    }

    /** Persona post-processing of the final answer text. */
    fun applyTone(text: String, persona: PersonaRow?): String {
        if (persona == null) return text
        var out = text
        val instr = persona.instructions.lowercase()
        val tone = persona.tone.lowercase()
        if (tone == "concise" || "concise" in instr || "fewest words" in instr) {
            val lines = out.lines()
            out = lines.take(7).joinToString("\n").take(650)
        }
        if ("no emoji" in instr) {
            out = out.replace(Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2190}-\\x{21FF}\\x{2B00}-\\x{2BFF}]"), "")
        }
        if (tone == "formal" && !out.startsWith("📌") && persona.name == "Boss") {
            out = "**Executive Summary (${persona.emoji} ${persona.name}):**\n$out"
        } else if (tone == "friendly" && persona.name == "ELI5") {
            out = "**${persona.emoji} Simple explanation:**\n$out"
        } else if (tone == "playful" || "emoji" in instr) {
            if (!out.contains("✨")) out = "✨ $out"
        }
        return out.trim()
    }
}
