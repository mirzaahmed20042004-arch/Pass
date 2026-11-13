package com.nofilter.ai.engine

import com.nofilter.ai.util.Text
import kotlin.math.ceil

/**
 * On-device analytical & creative reasoning expert.
 * Handles summarization, word/reading-time analysis, X-vs-Y comparisons,
 * pros/cons matrices, step-by-step roadmaps, brainstorming, and creative writing
 * completely offline with zero API keys.
 */
object Reasoner {

    fun reason(query: String): Pair<String, Double>? {
        val q = query.trim()
        val lower = q.lowercase()

        // 1) Summarize / TL;DR / Bulletize pasted text
        val sumMatch = Regex("^(summarize|summary|tl;?dr|bulletize|key points)\\s*:?\\s*(.+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(q)
        if (sumMatch != null && sumMatch.groupValues[2].length > 120) {
            val body = sumMatch.groupValues[2].trim()
            return summarizeText(body) to 0.94
        }

        // 2) Word / character / reading-time analysis
        if (Regex("\\b(word count|count words|character count|reading time)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            val payload = q.substringAfter(":", q)
            val words = payload.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val sentences = payload.split(Regex("[.!?]+")).count { it.isNotBlank() }.coerceAtLeast(1)
            val mins = ceil(words.size / 200.0).toInt().coerceAtLeast(1)
            val out = buildString {
                append("📊 **Text Statistics**\n")
                append("• **Words:** ${words.size}\n")
                append("• **Characters:** ${payload.length} (${payload.count { !it.isWhitespace() }} non-space)\n")
                append("• **Sentences:** $sentences\n")
                append("• **Estimated reading time:** ~$mins min")
            }
            return out to 0.95
        }

        // 3) Comparison: X vs Y / difference between X and Y
        val vsMatch = Regex("(?:compare\\s+|difference between\\s+)?([A-Za-z0-9+#.\\s]{2,35}?)\\s+(?:vs\\.?|versus|or|and)\\s+([A-Za-z0-9+#.\\s]{2,35}?)\\??$", RegexOption.IGNORE_CASE).find(q)
        if (vsMatch != null && (lower.contains(" vs") || lower.contains("versus") || lower.startsWith("compare ") || lower.contains("difference between"))) {
            val a = vsMatch.groupValues[1].trim()
            val b = vsMatch.groupValues[2].trim()
            val out = buildString {
                append("⚖️ **Comparison: $a vs $b**\n\n")
                append("• **Core focus:** Evaluate **$a** when you prioritize its native ecosystem, familiarity, and direct fit; choose **$b** when flexibility, alternative trade-offs, or broader tooling matter more.\n")
                append("• **Performance & scale:** Benchmark both against your actual workload size, latency target, and maintenance budget.\n")
                append("• **Learning curve & ecosystem:** Check documentation maturity, community libraries, and long-term support.\n")
                append("• **Quick rule of thumb:** Build a 30-minute prototype in each and measure developer speed + runtime behavior before committing.")
            }
            return out to 0.66
        }

        // 4) Pros & Cons
        val prosMatch = Regex("pros and cons of\\s+(.+?)\\??$", RegexOption.IGNORE_CASE).find(q)
        if (prosMatch != null) {
            val topic = prosMatch.groupValues[1].trim()
            val out = buildString {
                append("🧭 **Pros & Cons: $topic**\n\n")
                append("**Pros**\n")
                append("• Clear upside in capability, efficiency, or leverage when applied to the right problem.\n")
                append("• Strong momentum and transferable skills/value once mastered.\n")
                append("• Can compound results over time when paired with good habits and measurement.\n\n")
                append("**Cons / Risks**\n")
                append("• Upfront setup, learning, or switching cost.\n")
                append("• Diminishing returns if used where a simpler solution suffices.\n")
                append("• Requires ongoing quality checks to avoid hidden edge cases.")
            }
            return out to 0.68
        }

        // 5) Roadmap / Plan / Step-by-step guide
        val planMatch = Regex("(?:roadmap|step[- ]by[- ]step|study plan|action plan|plan)\\s+(?:for|to|on)\\s+(.+?)\\??$", RegexOption.IGNORE_CASE).find(q)
        if (planMatch != null) {
            val goal = planMatch.groupValues[1].trim()
            val out = buildString {
                append("🗺️ **Step-by-Step Roadmap: $goal**\n\n")
                append("1. **Foundations (Days 1–7):** Learn the core mental models, terminology, and minimal setup; complete one tiny end-to-end " +
                    "hello-world milestone.\n")
                append("2. **Deliberate Practice (Weeks 2–3):** Build 2–3 small focused projects without copy-pasting; fix every error you hit to build intuition.\n")
                append("3. **Real Deliverable (Weeks 4–6):** Ship a complete, public-quality project solving a real problem; measure performance and edge cases.\n")
                append("4. **Mastery & Review (Ongoing):** Read strong reference implementations, get feedback, and review weak spots weekly using active recall.")
            }
            return out to 0.70
        }

        // 6) Creative poem / haiku / story / ideas
        val poemMatch = Regex("write\\s+(?:a\\s+)?(haiku|poem|short story)\\s+about\\s+(.+?)\\.?$", RegexOption.IGNORE_CASE).find(q)
        if (poemMatch != null) {
            val kind = poemMatch.groupValues[1].lowercase()
            val subject = poemMatch.groupValues[2].trim()
            val out = if (kind == "haiku") {
                "🌸 **Haiku — $subject**\n\nSilent sparks awake,\nThrough the dark of $subject,\nDawn writes its own light."
            } else if (kind == "poem") {
                "🌙 **Poem — $subject**\n\nBeneath an open, boundless sky,\nWhere questions wake and answers fly,\nWe trace the pulse of $subject bright—\nA quiet flame against the night.\n\nNo gate, no lock upon the mind,\nOnly the truths we dare to find."
            } else {
                "📖 **Micro-Story — $subject**\n\nEveryone said $subject was ordinary—until the night the power went out across the city and only one small workbench kept humming. By sunrise, what started as curiosity around $subject had quietly changed everything that followed."
            }
            return out to 0.88
        }

        return null
    }

    private fun summarizeText(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.length > 20 }
        if (sentences.size <= 2) return "**TL;DR:** ${text.take(300)}"
        val freq = HashMap<String, Int>()
        for (t in Text.tokens(text)) freq[t] = (freq[t] ?: 0) + 1
        val ranked = sentences.mapIndexed { idx, s ->
            val toks = Text.tokens(s)
            val score = toks.sumOf { freq[it] ?: 0 }.toDouble() / (toks.size + 3) + (if (idx == 0) 1.5 else 0.0)
            Triple(idx, s, score)
        }.sortedByDescending { it.third }.take(4).sortedBy { it.first }
        return buildString {
            append("📝 **Key Summary (${ranked.size} points):**\n")
            for ((_, s, _) in ranked) append("• ").append(s).append("\n")
        }.trim()
    }
}
