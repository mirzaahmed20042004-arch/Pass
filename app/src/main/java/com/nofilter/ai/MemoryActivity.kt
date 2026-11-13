package com.nofilter.ai

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import com.nofilter.ai.db.Db
import com.nofilter.ai.db.MemoryRow
import com.nofilter.ai.ui.Ui

/** Long-term memory manager: facts the AI remembers about you and uses in answers. */
class MemoryActivity : BaseActivity() {

    private lateinit var db: Db
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private val adapter = MemAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_simple)
        db = Db(this)
        val p = Ui.palette(this)
        findViewById<TextView>(R.id.title).apply { text = getString(R.string.memory); setTextColor(p.text) }
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        empty.text = "No memories yet.\nTap + to teach NoFilter Ai something it should always remember (your name, preferences, projects…)."
        Ui.tint(findViewById(R.id.btn_back), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_add), p.accent)
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_add).setOnClickListener { showEditor(null) }
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, pos, _ ->
            showOptions(adapter.items[pos]); true
        }
        reload()
    }

    override fun onResume() { super.onResume(); reload() }

    private fun reload() {
        adapter.items = db.memories()
        adapter.notifyDataSetChanged()
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditor(m: MemoryRow?) {
        val et = EditText(this)
        et.setText(m?.text ?: "")
        et.hint = "e.g. My name is Ahmed. I prefer short answers in English."
        et.minLines = 3
        AlertDialog.Builder(this).setTitle(if (m == null) "Add memory" else "Edit memory")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val t = et.text.toString().trim()
                if (t.isNotBlank()) {
                    if (m == null) db.addMemory(t, "user") else db.updateMemory(m.id, t, m.tags)
                    reload()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showOptions(m: MemoryRow) {
        AlertDialog.Builder(this).setTitle("Memory")
            .setItems(arrayOf("Edit", "Delete")) { _, w ->
                when (w) {
                    0 -> showEditor(m)
                    1 -> { db.deleteMemory(m.id); reload() }
                }
            }.show()
    }

    private inner class MemAdapter : BaseAdapter() {
        var items: List<MemoryRow> = emptyList()
        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = items[pos].id
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_row, parent, false)
            val m = items[pos]
            val p = Ui.palette(this@MemoryActivity)
            v.findViewById<TextView>(R.id.emoji).text = "🧠"
            v.findViewById<TextView>(R.id.title).apply { text = m.text; setTextColor(p.text) }
            v.findViewById<TextView>(R.id.subtitle).apply {
                text = "saved ${Ui.fullTime(m.ts)} • used ${m.hits}×"
                setTextColor(p.muted)
            }
            v.findViewById<View>(R.id.trailing).visibility = View.GONE
            return v
        }
    }
}
