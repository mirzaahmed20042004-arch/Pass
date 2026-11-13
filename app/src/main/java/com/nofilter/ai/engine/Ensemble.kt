package com.nofilter.ai.engine

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import com.nofilter.ai.db.Db
import com.nofilter.ai.db.PersonaRow
import com.nofilter.ai.engine.WebResearch.Source
import com.nofilter.ai.util.Prefs
import java.util.concurrent.Executors

/**
 * NoFilter Ai Ensemble Engine.
 *
 * Combines 6 specialized zero-API-key experts directly on the device:
 *  1. 🌐 Web Research Expert (DuckDuckGo Instant + HTML/Lite + Bing fallback + Wikipedia REST + multi-page extraction)
 *  2. 🧠 Offline Knowledge Expert (TF-IDF + phrase retrieval over bundled kb.json)
 *  3. 🧮 Symbolic Math, Unit & Date Solver
 *  4. 💻 Code Generator & Stack-Trace Debugger
 *  5. 🔬 Analytical & Creative Reasoner (summarize, compare, pros/cons, roadmaps, creative writing)
 *  6. 💾 Auto-Memory & Trainable Persona Layer
 */
object Ensemble {

    data class Candidate(
        val expert: String,
        val icon: String,
        val text: String,
        val score: Double,
        val sources: List<Source> = emptyList()
    )

    interface Listener {
        fun onStatus(s: String)
        fun onChunk(s: String)
        fun onDone(finalText: String, sources: List<Source>, experts: String)
        fun onError(msg: String)
    }

    private val pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    private fun isOnline(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetworkInfo
            n != null && n.isConnected
        } catch (e: Exception) { true }
    }

    private val GREETINGS = Regex("^(hi|hello|hey|salam|aoa|salaam|yo|sup|good\\s+(morning|afternoon|evening))\\b", RegexOption.IGNORE_CASE)

    fun answer(
        ctx: Context,
        db: Db,
        rawQuery: String,
        webEnabled: Boolean,
        persona: PersonaRow?,
        listener: Listener
    ) {
        pool.execute {
            try {
                val status: (String) -> Unit = { s -> main.post { listener.onStatus(s) } }
                val query = rawQuery.trim()

                // Built-in slash commands for power users
                if (query.equals("/help", ignoreCase = true)) {
                    val help = """
                        ⚡ **NoFilter Ai Power Commands**
                        • `/remember <anything>` — save a fact to permanent memory
                        • `/memories` — list what NoFilter Ai remembers about you
                        • `/web <query>` — force live multi-source internet research
                        • `summarize: <long text>` — instant extractive TL;DR bullets
                        • `<math or unit conversion>` — e.g. `sqrt(144) + 18% of 250` or `72 f to c`
                        • Tap **📷** to extract text from any image offline with Tesseract OCR!
                    """.trimIndent()
                    stream(help, emptyList(), "⚡ commands", listener)
                    return@execute
                }

                val blocked = Safety.check(query)
                if (blocked != null) {
                    stream(blocked, emptyList(), "🛡️ safety", listener)
                    return@execute
                }

                // Auto-memory learning
                val learned = PersonaMemory.tryLearnFact(db, query)
                if (learned != null && learned.second) {
                    val msg = "🧠 **Memorized:** \"${learned.first}\"\n\nI've saved this to your on-device SQLite memory vault and will use it in future chats."
                    stream(msg, emptyList(), "💾 memory", listener)
                    return@execute
                }

                // Explicit memory recall query
                if (query.equals("/memories", ignoreCase = true) ||
                    Regex("^(what do you remember|what do you know about me)\\??$", RegexOption.IGNORE_CASE).matches(query)
                ) {
                    val all = db.memories()
                    val reply = if (all.isEmpty()) {
                        "🧠 I don't have any saved memories yet. Say **\"Remember that my name is …\"** or tap the 🧠 icon on the home screen to add some!"
                    } else {
                        "🧠 **Saved Long-Term Memories (${all.size}):**\n" + all.take(15).joinToString("\n") { "• ${it.text}" }
                    }
                    stream(reply, emptyList(), "💾 memory", listener)
                    return@execute
                }

                Knowledge.load(ctx)
                val effectiveQuery = if (query.lowercase().startsWith("/web ")) query.substring(5).trim() else query
                val forceWeb = query.lowercase().startsWith("/web ")
                val memories = PersonaMemory.matchMemories(db, effectiveQuery)

                val candidates = ArrayList<Candidate>()
                val useWeb = (webEnabled || forceWeb) && !Prefs.offlineMode && isOnline(ctx)

                // --- expert 1: symbolic math / unit / date solver (fast & exact) ---
                MathSolver.trySolve(effectiveQuery)?.let {
                    candidates.add(Candidate("math", "🧮", it, 1.0, emptyList()))
                }

                // --- expert 2: offline knowledge base ---
                val kbHits = Knowledge.query(effectiveQuery)
                if (kbHits.isNotEmpty()) {
                    val (entry, score) = kbHits.first()
                    candidates.add(Candidate("knowledge", "🧠", entry.answer, 0.58 + 0.34 * score, emptyList()))
                }

                // --- expert 3: code generator & stack-trace debugger ---
                CodeAssist.analyze(effectiveQuery)?.let {
                    candidates.add(Candidate("code", "💻", it, 0.88, emptyList()))
                }

                // --- expert 4: analytical & creative reasoner ---
                Reasoner.reason(effectiveQuery)?.let { (txt, sc) ->
                    candidates.add(Candidate("reasoner", "🔬", txt, sc, emptyList()))
                }

                // --- expert 5: conversationalist ---
                val isGreeting = GREETINGS.containsMatchIn(effectiveQuery) && effectiveQuery.length < 40
                if (isGreeting) {
                    val modeTag = persona?.name?.let { p -> " (${persona.emoji} $p mode)" } ?: ""
                    val reply = when (persona?.tone?.lowercase()) {
                        "formal" -> "Good day! How may I assist you today$modeTag?"
                        "casual", "friendly" -> "Hey there! 👋 What's on your mind today?"
                        "playful" -> "Yo! ✨ Ready to build, research, or solve something awesome?"
                        "poetic" -> "Hello, traveler under an open sky 🌙 — what shall we explore?"
                        else -> "Hello! 👋 Ask me anything, search the live web, solve math/code, or scan an image with offline OCR$modeTag."
                    }
                    candidates.add(Candidate("chat", "💬", reply, 0.96, emptyList()))
                }

                // --- expert 6: live web research (skip for pure greetings or pure math unless forced) ---
                if (useWeb && !isGreeting && (forceWeb || candidates.none { it.expert == "math" && it.score >= 1.0 })) {
                    val deep = forceWeb || effectiveQuery.contains(Regex("latest|news|today|current|2025|2026|who is|what happened", RegexOption.IGNORE_CASE))
                    val web = WebResearch.research(effectiveQuery, deep = deep, status = status)
                    if (web != null && web.text.isNotBlank()) {
                        val score = (0.80 + 0.04 * web.sources.size.coerceAtMost(5)).coerceAtMost(0.98)
                        candidates.add(Candidate("web", "🌐", web.text, score, web.sources))
                    }
                } else if (Prefs.offlineMode) {
                    status("Offline mode — using on-device experts only")
                }

                // --- ensemble ranker & combiner ---
                status("Combining ${candidates.size} expert models…")
                if (candidates.isEmpty()) {
                    val fallback = buildString {
                        if (memories.isNotEmpty()) {
                            append("**🧠 From your saved memory:**\n")
                            for (m in memories) append("• ${m.text}\n")
                        } else if (useWeb) {
                            append("I checked my on-device experts and searched the web for **\"${effectiveQuery.take(80)}\"**, ")
                            append("but didn't find high-confidence public sources. Try phrasing it with specific keywords (e.g. `/web <topic>`), or paste text with `summarize: ...`.")
                        } else {
                            append("I'm currently answering from **offline on-device experts** and don't have a specific entry for **\"${effectiveQuery.take(80)}\"** yet.\n\n")
                            append("Tap the **🌐 Globe icon** in the top bar (and disable Offline Mode in Settings if enabled) to let me research the live web — free and with zero API keys.")
                        }
                    }.trim()
                    val toned = PersonaMemory.applyTone(fallback, persona)
                    stream(toned, emptyList(), if (memories.isNotEmpty()) "💾memory" else "🤖ensemble", listener)
                    return@execute
                }

                val sorted = candidates.sortedByDescending { it.score }
                val primary = sorted.first()
                val sb = StringBuilder()
                val allSources = ArrayList<Source>()
                allSources.addAll(primary.sources)

                val math = candidates.find { it.expert == "math" }
                if (math != null && primary.expert != "math") {
                    sb.append(math.text).append("\n\n")
                }

                sb.append(primary.text.trim())

                // Merge complementary high-confidence experts
                for (c in sorted.drop(1)) {
                    allSources.addAll(c.sources)
                    if (c.expert == "math") continue
                    if (c.score >= 0.58 && c.expert != primary.expert) {
                        val snippet = c.text.lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n").take(450)
                        val label = when (c.expert) {
                            "knowledge" -> "Offline Knowledge Expert"
                            "web" -> "Live Web Findings"
                            "code" -> "Code & Technical Expert"
                            "reasoner" -> "Analytical Synthesis"
                            else -> "Additional Insight"
                        }
                        sb.append("\n\n**🧩 $label:**\n").append(snippet)
                    }
                }

                if (learned != null && !learned.second) {
                    sb.append("\n\n*(💾 Auto-saved to memory: \"${learned.first}\")*")
                } else if (memories.isNotEmpty()) {
                    sb.append("\n\n**🧠 Recalled from memory:** ")
                    sb.append(memories.joinToString(" • ") { it.text.take(120) })
                }

                if (persona != null && persona.id > 1) {
                    sb.append("\n\n_(Persona: ${persona.emoji} ${persona.name})_")
                }

                val finalText = PersonaMemory.applyTone(sb.toString().trim(), persona)
                val dedupSources = allSources.distinctBy { it.url }
                val experts = sorted.joinToString(" ") { "${it.icon}${it.expert}" }
                stream(finalText, dedupSources, experts, listener)
            } catch (e: Exception) {
                main.post { listener.onError("Something went wrong: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun stream(text: String, sources: List<Source>, experts: String, listener: Listener) {
        main.post {
            listener.onStatus("")
            val chunk = 32
            var i = 0
            fun step() {
                if (i >= text.length) {
                    listener.onDone(text, sources, experts)
                    return
                }
                val end = (i + chunk).coerceAtMost(text.length)
                listener.onChunk(text.substring(i, end))
                i = end
                main.postDelayed({ step() }, 14)
            }
            step()
        }
    }
}
