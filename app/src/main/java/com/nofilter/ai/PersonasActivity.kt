package com.nofilter.ai

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import com.nofilter.ai.db.Db
import com.nofilter.ai.db.PersonaRow
import com.nofilter.ai.ui.Ui
import com.nofilter.ai.util.Prefs

/**
 * "Train your AI": personas define how the AI behaves. The user can create
 * custom personas with their own instructions and tone — e.g. "behave with
 * me like my boss wants reports" — and pick one per chat.
 */
class PersonasActivity : BaseActivity() {

    private lateinit var db: Db
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private val adapter = PersonaAdapter()
    private val tones = arrayOf("Balanced", "Formal", "Casual", "Friendly", "Technical", "Playful", "Poetic", "Concise")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_simple)
        db = Db(this)
        Prefs.init(this)
        val p = Ui.palette(this)
        findViewById<TextView>(R.id.title).apply { text = getString(R.string.personas); setTextColor(p.text) }
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        empty.text = "Create a persona to train how NoFilter Ai behaves with you."
        Ui.tint(findViewById(R.id.btn_back), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_add), p.accent)
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_add).setOnClickListener { showEditor(null) }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val pr = adapter.items[pos]
            Prefs.defaultPersonaId = pr.id
            adapter.notifyDataSetChanged()
            Ui.toast(this, "Default persona: ${pr.emoji} ${pr.name}")
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val pr = adapter.items[pos]
            val opts = if (pr.isCustom) arrayOf("Edit", "Delete", "Set as default") else arrayOf("Set as default", "Duplicate as custom")
            AlertDialog.Builder(this).setTitle("${pr.emoji} ${pr.name}").setItems(opts) { _, w ->
                when {
                    opts[w] == "Edit" -> showEditor(pr)
                    opts[w] == "Delete" -> { db.deletePersona(pr.id); reload() }
                    opts[w] == "Set as default" -> { Prefs.defaultPersonaId = pr.id; adapter.notifyDataSetChanged(); Ui.toast(this, "Default set") }
                    opts[w] == "Duplicate as custom" -> showEditor(pr, duplicate = true)
                }
            }.show()
            true
        }
        reload()
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        adapter.items = db.personas()
        adapter.notifyDataSetChanged()
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditor(existing: PersonaRow?, duplicate: Boolean = false) {
        val p = Ui.palette(this)
        val ctx = this
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val nameEt = EditText(ctx).apply { hint = "Persona name (e.g. Boss)" }
        val emojiEt = EditText(ctx).apply { hint = "Emoji (e.g. 💼)"; setText(existing?.emoji ?: "🤖") }
        val instrEt = EditText(ctx).apply {
            hint = "Instructions — how should the AI behave? (e.g. 'Be direct. Give the answer first, then bullet-point details.')"
            minLines = 4
            gravity = android.view.Gravity.TOP
        }
        val toneSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, tones)
        }
        if (existing != null && !duplicate) {
            nameEt.setText(existing.name)
            instrEt.setText(existing.instructions)
            toneSpinner.setSelection(tones.indexOf(existing.tone).coerceAtLeast(0))
        } else if (duplicate) {
            nameEt.setText(existing?.name + " (custom)")
            instrEt.setText(existing?.instructions)
            toneSpinner.setSelection(tones.indexOf(existing?.tone ?: "Balanced").coerceAtLeast(0))
        }
        layout.addView(nameEt); layout.addView(emojiEt); layout.addView(instrEt); layout.addView(toneSpinner)
        AlertDialog.Builder(ctx).setTitle(if (existing == null || duplicate) "Train a new persona" else "Edit persona")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEt.text.toString().trim().ifBlank { "Custom persona" }
                val emoji = emojiEt.text.toString().trim().ifBlank { "🤖" }
                val instr = instrEt.text.toString().trim()
                val tone = tones[toneSpinner.selectedItemPosition]
                if (existing != null && !duplicate) db.updatePersona(existing.id, name, emoji, instr, tone)
                else db.addPersona(name, emoji, instr, tone, isCustom = true)
                reload()
            }.setNegativeButton("Cancel", null).show()
    }

    private inner class PersonaAdapter : BaseAdapter() {
        var items: List<PersonaRow> = emptyList()
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = items[pos].id
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_row, parent, false)
            val pr = items[pos]
            val p = Ui.palette(this@PersonasActivity)
            v.findViewById<TextView>(R.id.emoji).text = pr.emoji
            v.findViewById<TextView>(R.id.title).apply {
                text = pr.name + if (!pr.isCustom) "  (built-in)" else ""
                setTextColor(p.text)
            }
            v.findViewById<TextView>(R.id.subtitle).apply {
                text = "Tone: ${pr.tone} • ${pr.instructions.take(90)}"
                setTextColor(p.muted)
            }
            val trailing = v.findViewById<android.widget.ImageView>(R.id.trailing)
            if (pr.id == Prefs.defaultPersonaId) {
                trailing.visibility = View.VISIBLE
                Ui.tint(trailing, p.ok)
            } else trailing.visibility = View.GONE
            return v
        }
    }
}
