package com.nofilter.ai.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nofilter.ai.util.Prefs
import java.io.File
import java.io.FileOutputStream

/**
 * On-device OCR engine: Tesseract (LSTM, WebAssembly) running inside a hidden
 * WebView, fully offline, unlimited, no API key. The traineddata + wasm core
 * are bundled in app assets and served by [AssetServer].
 */
class OcrEngine(private val context: Context, private val webView: WebView) {

    interface Callback {
        fun onProgress(status: String, pct: Int)
        fun onResult(text: String, confidence: Double)
        fun onError(msg: String)
        fun onReady()
    }

    private var server: AssetServer? = null
    private var callback: Callback? = null
    @Volatile private var ready = false

    @SuppressLint("SetJavaScriptEnabled")
    fun init(cb: Callback) {
        callback = cb
        Prefs.init(context.applicationContext)
        server = AssetServer(context.assets, File(context.cacheDir, "ocr_tmp").apply { mkdirs() })
        server!!.start()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                cb.onReady()
            }
        }
        webView.addJavascriptInterface(Bridge(), "NF")
        webView.loadUrl("${server!!.baseUrl}/ocr.html")
    }

    fun shutdown() {
        server?.stop()
    }

    /** Recognize text in [imageFile]. Result delivered via callback. */
    fun recognize(imageFile: File, lang: String = Prefs.ocrLang) {
        if (!ready) {
            callback?.onError("OCR engine still loading…")
            return
        }
        val tmp = File(File(context.cacheDir, "ocr_tmp"), "scan_${System.currentTimeMillis()}.jpg")
        try {
            // downscale very large images for speed
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(imageFile.absolutePath, opts)
            val maxDim = 2200
            var sample = 1
            while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
            val opts2 = BitmapFactory.Options()
            opts2.inSampleSize = sample
            val bmp = BitmapFactory.decodeFile(imageFile.absolutePath, opts2)
            if (bmp == null) {
                callback?.onError("Could not decode image")
                return
            }
            FileOutputStream(tmp).use { fos ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            bmp.recycle()
        } catch (e: Exception) {
            // fall back to original file
            tmp.delete()
            tmp.writeBytes(imageFile.readBytes())
        }
        val url = "${server!!.baseUrl}/tmp/${tmp.name}"
        val js = "window.nfOcr(${jsString(url)}, ${jsString(lang)})"
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun jsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun progress(pct: Int, status: String) {
            callback?.onProgress(status, pct)
        }

        @JavascriptInterface
        fun done(json: String) {
            try {
                val o = org.json.JSONObject(json)
                callback?.onResult(o.optString("text"), o.optDouble("confidence", 0.0))
            } catch (e: Exception) {
                callback?.onError("Bad OCR result: ${e.message}")
            }
        }

        @JavascriptInterface
        fun error(msg: String) {
            callback?.onError(msg)
        }
    }
}
