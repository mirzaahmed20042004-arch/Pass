package com.nofilter.ai.engine

import com.nofilter.ai.util.Html2Text
import com.nofilter.ai.util.Http
import com.nofilter.ai.util.Text
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Live web-research expert. Runs entirely from the device with plain HTTP
 * (no API keys): DuckDuckGo instant answers + HTML/lite result pages,
 * Wikipedia's open API, and direct fetching of the top result pages.
 */
object WebResearch {

    data class Source(val title: String, val url: String, val snippet: String)
    data class WebAnswer(val text: String, val sources: List<Source>)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun research(query: String, deep: Boolean, status: (String) -> Unit): WebAnswer? {
        val sources = LinkedHashMap<String, Source>() // url -> source
        val snippets = ArrayList<String>()

        // 1) DuckDuckGo instant answers (no key)
        status("Checking instant answers…")
        var quickAnswer: String? = null
        try {
            val j = Http.getText("https://api.duckduckgo.com/?q=${enc(query)}&format=json&no_html=1&no_redirect=1&skip_disambig=1", timeoutMs = 8000)
            if (j != null) {
                val o = JSONObject(j)
                val parts = ArrayList<String>()
                o.optString("AbstractText").takeIf { it.isNotBlank() }?.let { parts.add(it) }
                o.optString("Definition").takeIf { it.isNotBlank() && it != o.optString("AbstractText") }?.let { parts.add(it) }
                o.optString("AbstractURL").takeIf { it.isNotBlank() }?.let { url ->
                    val t = o.optString("AbstractSource").ifBlank { url }
                    if (!sources.containsKey(url)) sources[url] = Source(t, url, o.optString("AbstractText").take(160))
                }
                o.optJSONArray("RelatedTopics")?.let { arr ->
                    for (i in 0 until arr.length().coerceAtMost(6)) {
                        val t = arr.optJSONObject(i) ?: continue
                        val txt = t.optString("Text")
                        val url = t.optString("FirstURL")
                        if (txt.isNotBlank() && url.isNotBlank() && !sources.containsKey(url)) {
                            sources[url] = Source(txt.take(80), url, txt.take(160))
                            if (parts.size < 3 && txt.isNotBlank()) parts.add(txt)
                        }
                    }
                }
                if (parts.isNotEmpty()) quickAnswer = parts.joinToString("\n\n")
            }
        } catch (e: Exception) { /* ignore */ }

        // 2) DuckDuckGo HTML results
        status("Searching the web…")
        val ddgLinks = ArrayList<Source>()
        try {
            val html = Http.getText("https://html.duckduckgo.com/html/?q=${enc(query)}", timeoutMs = 9000)
                ?: Http.getText("https://lite.duckduckgo.com/lite/?q=${enc(query)}", timeoutMs = 9000)
            if (html != null) {
                parseDdg(html, ddgLinks)
            }
        } catch (e: Exception) { /* ignore */ }
        if (ddgLinks.isEmpty()) {
            // fallback: Bing
            try {
                val html = Http.getText("https://www.bing.com/search?q=${enc(query)}&count=15", timeoutMs = 9000)
                if (html != null) parseBing(html, ddgLinks)
            } catch (e: Exception) { /* ignore */ }
        }
        for (s in ddgLinks) if (!sources.containsKey(s.url)) sources[s.url] = s

        // 3) Wikipedia open API
        status("Checking Wikipedia…")
        var wikiExtract: String? = null
        try {
            val search = Http.getText(
                "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${enc(query)}&format=json&srlimit=3",
                timeoutMs = 8000
            )
            val titles = ArrayList<String>()
            if (search != null) {
                val arr = JSONObject(search).optJSONObject("query")?.optJSONArray("search")
                if (arr != null) for (i in 0 until arr.length()) titles.add(arr.getJSONObject(i).optString("title"))
            }
            if (titles.isNotEmpty()) {
                val summary = Http.getText(
                    "https://en.wikipedia.org/api/rest_v1/page/summary/${enc(titles[0])}",
                    timeoutMs = 8000
                )
                if (summary != null) {
                    val o = JSONObject(summary)
                    wikiExtract = o.optString("extract").takeIf { it.isNotBlank() }
                    val url = o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")?.takeIf { it.isNotBlank() }
                    if (url != null && !sources.containsKey(url)) {
                        sources[url] = Source("Wikipedia — ${titles[0]}", url, wikiExtract?.take(160) ?: "")
                    }
                }
            }
        } catch (e: Exception) { /* ignore */ }

        // 4) Fetch top pages and extract relevant sentences
        val pageLimit = if (deep) 5 else 3
        status("Reading ${pageLimit.coerceAtMost(sources.size)} sources…")
        val pageTexts = ArrayList<Pair<Source, String>>()
        var count = 0
        for ((url, src) in sources.toList()) {
            if (count >= pageLimit) break
            if (url.contains("youtube.com") || url.contains("facebook.com") || url.contains("instagram.com")) continue
            val page = Http.getText(url, timeoutMs = 8000, referer = "https://duckduckgo.com/", maxBytes = 400000)
            if (page != null) {
                val text = Html2Text.toText(page)
                if (text.length > 200) {
                    val rel = Html2Text.relevantSentences(text, query, maxSentences = if (deep) 7 else 4, maxChars = 900)
                    pageTexts.add(src to rel)
                    snippets.add(rel)
                    count++
                }
            }
        }

        // 5) Compose cited answer
        if (quickAnswer == null && pageTexts.isEmpty() && wikiExtract == null && sources.isEmpty()) return null
        status("Combining findings…")
        val sb = StringBuilder()
        val citedSources = ArrayList<Source>()
        var idx = 0

        fun cite(src: Source): String {
            val i = citedSources.indexOfFirst { it.url == src.url }
            return if (i >= 0) "[${i + 1}]" else {
                citedSources.add(src)
                "[${citedSources.size}]"
            }
        }

        if (!quickAnswer.isNullOrBlank()) {
            sb.append("**Quick answer:** ").append(quickAnswer.trim()).append("\n\n")
        }
        if (!wikiExtract.isNullOrBlank()) {
            val wikiSrc = citedSources.find { it.title.startsWith("Wikipedia") } ?: sources.values.firstOrNull { it.title.startsWith("Wikipedia") }
            sb.append("**Wikipedia:** ").append(wikiExtract.trim().take(700))
            if (wikiSrc != null) sb.append(" ").append(cite(wikiSrc))
            sb.append("\n\n")
        }
        if (pageTexts.isNotEmpty()) {
            sb.append("**What the sources say:**\n")
            for ((src, txt) in pageTexts) {
                val first = txt.lineSequence().firstOrNull()?.trim().orEmpty()
                val rest = txt.lineSequence().drop(1).joinToString(" ").trim()
                sb.append("• ").append(first)
                if (rest.isNotBlank()) sb.append(" ").append(rest.take(320))
                sb.append(" ").append(cite(src)).append("\n")
            }
            sb.append("\n")
        }
        if (sb.isEmpty() && sources.isNotEmpty()) {
            sb.append("I found these relevant pages:\n")
            for ((_, src) in sources.toList().take(6)) {
                sb.append("• [${src.title.take(90)}](${src.url}) — ${src.snippet.take(120)}\n")
            }
        }
        val text = sb.toString().trim()
        return WebAnswer(text, citedSources.ifEmpty { sources.values.toList().take(8) })
    }

    private fun parseDdg(html: String, out: MutableList<Source>) {
        // result links: <a class="result__a" href="//duckduckgo.com/l/?uddg=ENCODED&rut=...">
        val linkRe = Regex("""result__a"[^>]*href="([^"]+)"""")
        val titleRe = Regex("""result__a"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val snippetRe = Regex("""result__snippet"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val links = linkRe.findAll(html).map { it.groupValues[1] }.toList()
        val titles = titleRe.findAll(html).map { Html2Text.toText(it.groupValues[1]).trim() }.toList()
        val snippets = snippetRe.findAll(html).map { Html2Text.toText(it.groupValues[1]).trim() }.toList()
        for (i in links.indices.take(10)) {
            var href = links[i]
            if (href.contains("uddg=")) {
                val m = Regex("uddg=([^&]+)").find(href)
                if (m != null) href = java.net.URLDecoder.decode(m.groupValues[1], "UTF-8")
            }
            if (!href.startsWith("http")) continue
            val title = titles.getOrNull(i)?.takeIf { it.isNotBlank() } ?: href
            val snip = snippets.getOrNull(i)?.take(200) ?: ""
            out.add(Source(title.take(120), href, snip))
        }
    }

    private fun parseBing(html: String, out: MutableList<Source>) {
        val re = Regex("""<li class="b_algo"[^>]*>.*?<h2><a href="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in re.findAll(html).take(10)) {
            val url = m.groupValues[1]
            val title = Html2Text.toText(m.groupValues[2]).trim()
            if (url.startsWith("http")) out.add(Source(title.take(120).ifBlank { url }, url, ""))
        }
    }
}
