package com.nofilter.ai.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Minimal dependency-free HTTP client used by the web-research engine. */
object Http {

    private val USER_AGENTS = arrayOf(
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
    )

    private var uaIndex = 0
    private fun nextUa(): String {
        val ua = USER_AGENTS[uaIndex % USER_AGENTS.size]
        uaIndex++
        return ua
    }

    data class Response(val code: Int, val bytes: ByteArray) {
        fun text(): String {
            var s = String(bytes, Charsets.UTF_8)
            // crude charset fix for latin-1 pages
            if (s.contains('\uFFFD')) s = String(bytes, Charsets.ISO_8859_1)
            return s
        }
    }

    /** GET a URL, following redirects. Returns null on failure. Max 1.5 MB body. */
    fun get(url: String, timeoutMs: Int = 9000, referer: String? = null, maxBytes: Int = 1500000): Response? {
        var current = url
        for (hop in 0 until 5) {
            try {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", nextUa())
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                conn.setRequestProperty("Accept-Encoding", "gzip")
                if (referer != null) conn.setRequestProperty("Referer", referer)
                val code = conn.responseCode
                if (code in 301..308 || code == 307 || code == 308) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc == null) return null
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code !in 200..299) { conn.disconnect(); return null }
                var stream: InputStream = conn.inputStream
                if (conn.getHeaderField("Content-Encoding")?.contains("gzip") == true) {
                    stream = GZIPInputStream(stream)
                }
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16384)
                var total = 0
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    if (total + n > maxBytes) {
                        out.write(buf, 0, maxBytes - total)
                        break
                    }
                    out.write(buf, 0, n)
                    total += n
                }
                stream.close()
                conn.disconnect()
                return Response(code, out.toByteArray())
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    fun getText(url: String, timeoutMs: Int = 9000, referer: String? = null, maxBytes: Int = 1500000): String? =
        get(url, timeoutMs, referer, maxBytes)?.text()

    fun isReachable(): Boolean {
        return try {
            val r = get("https://duckduckgo.com", timeoutMs = 6000, maxBytes = 2048)
            r != null && r.code in 200..399
        } catch (e: Exception) {
            false
        }
    }
}
