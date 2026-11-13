package com.nofilter.ai.engine

import android.content.Context
import com.nofilter.ai.util.Text
import org.json.JSONArray
import java.io.File
import kotlin.math.ln

/**
 * Offline knowledge engine: a bundled knowledge base (assets/web/kb.json)
 * retrieved with TF-IDF style scoring. Runs fully on-device.
 */
object Knowledge {

    data class Entry(val keywords: List<String>, val answer: String, val category: String, val tokens: Set<String>)

    private var entries: List<Entry> = emptyList()
    private var idf: Map<String, Double> = emptyMap()
    private var loadedFrom: String? = null

    @Synchronized
    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        try {
            val json = context.assets.open("web/kb.json").use { it.readBytes().toString(Charsets.UTF_8) }
            loadFromString(json)
            loadedFrom = "assets"
        } catch (e: Exception) {
            entries = emptyList()
        }
    }

    @Synchronized
    fun loadFromString(json: String) {
        val arr = JSONArray(json)
        val list = ArrayList<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kws = mutableListOf<String>()
            o.optJSONArray("k")?.let { a -> for (j in 0 until a.length()) kws.add(a.getString(j)) }
            val ans = o.optString("a")
            val cat = o.optString("c", "general")
            val allTokens = (kws.flatMap { Text.tokens(it) } + Text.tokens(ans)).toSet()
            list.add(Entry(kws, ans, cat, allTokens))
        }
        entries = list
        // idf over entries
        val df = HashMap<String, Int>()
        for (e in entries) for (t in e.tokens) df[t] = (df[t] ?: 0) + 1
        idf = df.mapValues { (_, v) -> ln((entries.size + 1.0) / (v + 1.0)) + 1.0 }
    }

    @Synchronized
    fun isLoaded(): Boolean = entries.isNotEmpty()

    /** Returns best matching entries with normalized score 0..1 */
    fun query(q: String, topK: Int = 2): List<Pair<Entry, Double>> {
        if (entries.isEmpty()) return emptyList()
        val qTokens = Text.tokens(q)
        if (qTokens.isEmpty()) return emptyList()
        val scored = entries.map { e ->
            var score = 0.0
            for (t in qTokens) {
                if (t in e.tokens) score += (idf[t] ?: 1.0)
            }
            // keyword phrase bonus
            val ql = q.lowercase()
            for (k in e.keywords) {
                if (k.length > 3 && ql.contains(k.lowercase())) score += 3.0
            }
            // length normalization
            score = score / (1.0 + ln((e.tokens.size + 1).toDouble()))
            e to score
        }.filter { it.second > 0.35 }.sortedByDescending { it.second }
        if (scored.isEmpty()) return emptyList()
        val max = scored.first().second
        return scored.take(topK).map { (e, s) -> e to (s / max).coerceIn(0.0, 1.0) }
    }

    fun size(): Int = entries.size
}
