package com.nofilter.ai.ocr

import android.content.res.AssetManager
import java.io.File
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Tiny HTTP server bound to 127.0.0.1 that serves the app's web assets
 * (OCR engine) and temporary files (images to scan). Used only inside the
 * app process; never exposed externally.
 */
class AssetServer(private val assets: AssetManager, private val tmpDir: File) {

    @Volatile var port: Int = 0
        private set
    private var server: ServerSocket? = null
    @Volatile private var running = false

    val baseUrl: String get() = "http://127.0.0.1:$port"

    fun start() {
        if (running) return
        val ss = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        server = ss
        port = ss.localPort
        running = true
        thread(name = "nf-asset-server", isDaemon = true) {
            while (running) {
                try {
                    val client = ss.accept()
                    thread(isDaemon = true) { handle(client) }
                } catch (e: Exception) {
                    if (running) { /* keep serving */ }
                }
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 15000
            val input = client.getInputStream()
            val out = client.getOutputStream()
            val header = readLine(input) ?: return
            val parts = header.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].split("?")[0]
            // drain headers
            while (true) {
                val l = readLine(input) ?: break
                if (l.isEmpty()) break
            }
            if (method != "GET" && method != "HEAD") {
                writeHead(out, 405, "text/plain", 0)
                out.flush(); client.close(); return
            }
            if (path.startsWith("/tmp/")) {
                val name = path.removePrefix("/tmp/")
                if (!name.matches(Regex("[A-Za-z0-9._-]+")) || name.contains("..")) {
                    writeHead(out, 403, "text/plain", 0); out.flush(); client.close(); return
                }
                val f = File(tmpDir, name)
                serveFile(out, f, method == "HEAD")
            } else {
                val rel = if (path == "/" || path.isEmpty()) "/ocr.html" else path
                serveAsset(out, "web$rel", method == "HEAD")
            }
            out.flush()
            client.close()
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun serveAsset(out: OutputStream, assetPath: String, headOnly: Boolean) {
        try {
            val stream = assets.open(assetPath)
            val bytes = stream.readBytes()
            stream.close()
            writeHead(out, 200, mimeOf(assetPath), bytes.size)
            if (!headOnly) out.write(bytes)
        } catch (e: Exception) {
            writeHead(out, 404, "text/plain", 0)
        }
    }

    private fun serveFile(out: OutputStream, f: File, headOnly: Boolean) {
        if (!f.exists() || !f.isFile) {
            writeHead(out, 404, "text/plain", 0)
            return
        }
        writeHead(out, 200, mimeOf(f.name), f.length().toInt())
        if (!headOnly) {
            f.inputStream().use { it.copyTo(out) }
        }
    }

    private fun writeHead(out: OutputStream, code: Int, mime: String, len: Int) {
        val reason = mapOf(200 to "OK", 403 to "Forbidden", 404 to "Not Found", 405 to "Method Not Allowed").getOrDefault(code, "OK")
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $code $reason\r\n")
        sb.append("Content-Type: $mime\r\n")
        sb.append("Content-Length: $len\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun mimeOf(path: String): String = when {
        path.endsWith(".html", true) -> "text/html; charset=utf-8"
        path.endsWith(".js", true) -> "application/javascript; charset=utf-8"
        path.endsWith(".mjs", true) -> "application/javascript; charset=utf-8"
        path.endsWith(".wasm", true) -> "application/wasm"
        path.endsWith(".css", true) -> "text/css; charset=utf-8"
        path.endsWith(".json", true) -> "application/json; charset=utf-8"
        path.endsWith(".png", true) -> "image/png"
        path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
        path.endsWith(".webp", true) -> "image/webp"
        path.endsWith(".traineddata", true) -> "application/octet-stream"
        path.endsWith(".txt", true) -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (prev == '\r'.code) {
                    return sb.substring(0, sb.length - 1)
                }
                return sb.toString()
            }
            sb.append(b.toChar())
            prev = b
        }
    }
}
