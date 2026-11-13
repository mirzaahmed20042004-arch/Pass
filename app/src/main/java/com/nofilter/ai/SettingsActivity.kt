package com.nofilter.ai

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.nofilter.ai.db.Db
import com.nofilter.ai.ui.Ui
import com.nofilter.ai.util.Prefs
import java.io.File

class SettingsActivity : BaseActivity() {

    private lateinit var db: Db
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        db = Db(this)
        Prefs.init(this)
        val p = Ui.palette(this)
        container = findViewById(R.id.settings_container)
        Ui.tint(findViewById(R.id.btn_back), p.text)
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        build()
    }

    private fun build() {
        val p = Ui.palette(this)
        container.removeAllViews()

        // Theme
        addSection("Appearance")
        val themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Dark", "Light"))
            setSelection(Prefs.theme)
        }
        themeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (Prefs.theme != pos) {
                    Prefs.theme = pos
                    recreate()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        addRow("Theme", themeSpinner)

        // Text size
        val seek = SeekBar(this).apply { max = 45; progress = ((Prefs.fontScale - 0.85f) / 0.45f * 45).toInt() }
        val sizeLabel = TextView(this).apply { setTextColor(p.muted); text = "${(Prefs.fontScale * 100).toInt()}%" }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                Prefs.fontScale = 0.85f + progress / 45f * 0.45f
                sizeLabel.text = "${(Prefs.fontScale * 100).toInt()}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        addRow("Chat text size  ${sizeLabel.text}", seek, sizeLabel)

        // Web research default
        addSection("AI engine")
        val webSwitch = Switch(this).apply { isChecked = Prefs.webResearchDefault }
        webSwitch.setOnCheckedChangeListener { _, v -> Prefs.webResearchDefault = v }
        addRow("Web research by default (live internet, no API key)", webSwitch)

        val offlineSwitch = Switch(this).apply { isChecked = Prefs.offlineMode }
        offlineSwitch.setOnCheckedChangeListener { _, v -> Prefs.offlineMode = v }
        addRow("Offline mode (on-device knowledge only)", offlineSwitch)

        // Data
        addSection("Data")
        addButton("Export chats & memories (JSON)") { exportData() }
        addButton("Import from JSON") { importData() }
        addButton("Clear all data", danger = true) {
            AlertDialog.Builder(this).setTitle("Clear everything?")
                .setMessage("All chats, messages, memories and custom personas will be deleted.")
                .setPositiveButton("Clear") { _, _ -> db.clearAll(); Ui.toast(this, "Cleared") }
                .setNegativeButton("Cancel", null).show()
        }

        // About
        addSection("About")
        addButton("About NoFilter Ai") { showAbout() }
    }

    private fun addSection(title: String) {
        val ctx = this
        val p = Ui.palette(ctx)
        val tv = TextView(ctx).apply {
            text = title.uppercase()
            setTextColor(p.accent2)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, Ui.dp(ctx, 18f), 0, Ui.dp(ctx, 6f))
        }
        container.addView(tv)
    }

    private fun addRow(label: String, widget: View, trailing: View? = null) {
        val ctx = this
        val p = Ui.palette(ctx)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(ctx, 8f), 0, Ui.dp(ctx, 8f))
        }
        val tv = TextView(ctx).apply {
            text = label
            setTextColor(p.text)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(tv)
        if (trailing != null) {
            trailing.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(ctx, 8f) }
            row.addView(trailing)
        }
        widget.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(widget)
        container.addView(row)
        val div = View(ctx).apply {
            setBackgroundColor(p.border)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        }
        container.addView(div)
    }

    private fun addButton(label: String, danger: Boolean = false, onClick: () -> Unit) {
        val ctx = this
        val p = Ui.palette(ctx)
        val b = Button(ctx).apply {
            text = label
            setTextColor(if (danger) p.danger else p.text)
            background = Ui.inputBackground(p)
            isAllCaps = false
            setOnClickListener { onClick() }
            setPadding(Ui.dp(ctx, 16f), Ui.dp(ctx, 12f), Ui.dp(ctx, 16f), Ui.dp(ctx, 12f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(ctx, 6f); bottomMargin = Ui.dp(ctx, 6f)
            }
        }
        container.addView(b)
    }

    private fun exportData() {
        try {
            val json = db.exportAll()
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "nofilter-ai-export.json")
            pendingExport = json
            startActivityForResult(i, REQ_EXPORT)
        } catch (e: Exception) {
            Ui.toast(this, "Export failed: ${e.message}")
        }
    }

    private fun importData() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        startActivityForResult(i, REQ_IMPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        if (requestCode == REQ_EXPORT && pendingExport != null) {
            try {
                contentResolver.openOutputStream(data.data!!)?.use { it.write(pendingExport!!.toByteArray()) }
                Ui.toast(this, "Exported ✓")
            } catch (e: Exception) { Ui.toast(this, "Export failed: ${e.message}") }
            pendingExport = null
        } else if (requestCode == REQ_IMPORT) {
            try {
                val json = contentResolver.openInputStream(data.data!!)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                val ok = db.importAll(json)
                Ui.toast(this, if (ok) "Imported ✓" else "Import failed — invalid file")
            } catch (e: Exception) { Ui.toast(this, "Import failed: ${e.message}") }
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("NoFilter Ai 🤖")
            .setMessage(
                "Version 1.0.0\n\n" +
                "• Unlimited AI chat — no API keys, no sign-up, no rate limits\n" +
                "• Ensemble engine: live web research + offline knowledge + math solver + code assistant + memory, combined on your device\n" +
                "• Trainable personas — teach it how to behave with you\n" +
                "• Long-term memory\n" +
                "• Free unlimited on-device OCR (Tesseract, no API key)\n" +
                "• Voice input & read-aloud\n\n" +
                "Web research uses your device's internet connection directly (DuckDuckGo, Wikipedia and open pages). Everything else runs 100% on-device."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private var pendingExport: String? = null
    private companion object {
        const val REQ_EXPORT = 9301
        const val REQ_IMPORT = 9302
    }
}
