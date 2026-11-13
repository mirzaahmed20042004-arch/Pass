package com.nofilter.ai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.webkit.WebView
import com.nofilter.ai.ocr.OcrEngine
import com.nofilter.ai.ui.Ui
import com.nofilter.ai.util.Prefs
import java.io.File

/**
 * OCR Studio: free, unlimited, fully on-device OCR (Tesseract LSTM wasm,
 * bundled traineddata — no API key, no network).
 */
class OcrActivity : BaseActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"       // auto-scan this file
        const val EXTRA_RESULT_TEXT = "result_text"     // returned to caller
        private const val REQ_PICK = 8801
        private const val REQ_CAMERA = 8802
    }

    private lateinit var preview: ImageView
    private lateinit var btnOcr: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var resultActions: View
    private lateinit var webView: WebView
    private var engine: OcrEngine? = null
    private var currentImage: File? = null
    private var cameraUri: Uri? = null
    private var autoScan = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)
        Prefs.init(this)
        val p = Ui.palette(this)

        preview = findViewById(R.id.preview)
        btnOcr = findViewById(R.id.btn_ocr)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        result = findViewById(R.id.result)
        resultActions = findViewById(R.id.result_actions)
        webView = findViewById(R.id.ocr_webview)
        Ui.tint(findViewById(R.id.btn_back), p.text)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_pick).setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            startActivityForResult(Intent.createChooser(i, "Pick image"), REQ_PICK)
        }
        findViewById<Button>(R.id.btn_camera).setOnClickListener { openCamera() }
        btnOcr.setOnClickListener { runOcr() }
        findViewById<Button>(R.id.btn_copy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("OCR text", result.text))
            Ui.toast(this, "Copied to clipboard")
        }
        findViewById<Button>(R.id.btn_share).setOnClickListener {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, result.text.toString())
            startActivity(Intent.createChooser(i, "Share OCR text"))
        }

        engine = OcrEngine(this, webView)
        engine!!.init(object : OcrEngine.Callback {
            override fun onProgress(s: String, pct: Int) {
                runOnUiThread {
                    Ui.show(progress)
                    progress.progress = pct
                    status.text = s
                }
            }

            override fun onResult(text: String, confidence: Double) {
                runOnUiThread {
                    Ui.hide(progress)
                    status.text = "Done • confidence ${String.format("%.1f", confidence)}% • on-device"
                    result.text = text.ifBlank { "(no text found in image)" }
                    Ui.show(result)
                    Ui.show(resultActions)
                }
            }

            override fun onError(msg: String) {
                runOnUiThread {
                    Ui.hide(progress)
                    status.text = "Error"
                    Ui.toast(this@OcrActivity, msg)
                }
            }

            override fun onReady() {
                runOnUiThread { status.text = "OCR engine ready (on-device Tesseract)" }
                if (autoScan) runOcr()
            }
        })

        // auto-scan path (from chat attach)
        intent.getStringExtra(EXTRA_IMAGE_PATH)?.let { path ->
            val f = File(path)
            if (f.exists()) {
                currentImage = f
                autoScan = true
                showImage(f)
                btnOcr.isEnabled = true
            }
        }
    }

    private fun showImage(f: File) {
        try {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp != null) {
                preview.setImageBitmap(bmp)
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun openCamera() {
        try {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DESCRIPTION, "nofilter_scan")
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            cameraUri = uri
            val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            startActivityForResult(i, REQ_CAMERA)
        } catch (e: Exception) {
            Ui.toast(this, "Camera not available: ${e.message}")
        }
    }

    private fun runOcr() {
        val f = currentImage ?: run { Ui.toast(this, "Pick an image first"); return }
        autoScan = false
        result.visibility = View.GONE
        resultActions.visibility = View.GONE
        status.text = "Starting OCR…"
        engine?.recognize(f, Prefs.ocrLang)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data?.data != null) {
            try {
                val tmp = File(cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(data.data!!)?.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                currentImage = tmp
                showImage(tmp)
                btnOcr.isEnabled = true
                status.text = "Image loaded — tap Extract text"
            } catch (e: Exception) {
                Ui.toast(this, "Could not read image")
            }
        } else if (requestCode == REQ_CAMERA && resultCode == RESULT_OK) {
            val uri = cameraUri ?: data?.data ?: return
            try {
                val tmp = File(cacheDir, "ocr_cam_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                currentImage = tmp
                showImage(tmp)
                btnOcr.isEnabled = true
                status.text = "Photo captured — tap Extract text"
            } catch (e: Exception) {
                Ui.toast(this, "Could not read photo")
            }
        }
    }

    override fun onBackPressed() {
        // if launched from chat with a result, return it
        if (intent.hasExtra(EXTRA_IMAGE_PATH) && result.visibility == View.VISIBLE) {
            val out = Intent()
            out.putExtra(EXTRA_RESULT_TEXT, result.text.toString())
            setResult(Activity.RESULT_OK, out)
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.shutdown()
    }
}
