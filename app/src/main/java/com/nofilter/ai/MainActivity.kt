package com.nofilter.ai

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import com.nofilter.ai.db.ChatRow
import com.nofilter.ai.db.Db
import com.nofilter.ai.ui.Ui
import com.nofilter.ai.util.Prefs

/** Home screen: list of chats + entry points to OCR, Memory, Personas, Settings. */
class MainActivity : BaseActivity() {

    private lateinit var db: Db
    private lateinit var list: ListView
    private lateinit var empty: View
    private lateinit var searchInput: EditText
    private var filter = ""
    private val adapter = ChatAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = Db(this)
        Prefs.init(this)

        val p = Ui.palette(this)
        list = findViewById(R.id.chat_list)
        empty = findViewById(R.id.empty_view)
        searchInput = findViewById(R.id.search_input)
        list.adapter = adapter

        Ui.tint(findViewById<ImageButton>(R.id.btn_search), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_ocr), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_memory), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_personas), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_settings), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.fab_new_chat), p.surface)

        findViewById<ImageButton>(R.id.btn_search).setOnClickListener {
            val show = searchInput.visibility != View.VISIBLE
            searchInput.visibility = if (show) View.VISIBLE else View.GONE
            if (show) searchInput.requestFocus() else { filter = ""; searchInput.text.clear(); reload() }
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter = s.toString(); reload() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        findViewById<ImageButton>(R.id.btn_ocr).setOnClickListener { startActivity(Intent(this, OcrActivity::class.java)) }
        findViewById<ImageButton>(R.id.btn_memory).setOnClickListener { startActivity(Intent(this, MemoryActivity::class.java)) }
        findViewById<ImageButton>(R.id.btn_personas).setOnClickListener { startActivity(Intent(this, PersonasActivity::class.java)) }
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<ImageButton>(R.id.fab_new_chat).setOnClickListener { openNewChat() }

        list.setOnItemClickListener { _, _, pos, _ ->
            val row = adapter.items[pos]
            val i = Intent(this, ChatActivity::class.java)
            i.putExtra(ChatActivity.EXTRA_CHAT_ID, row.id)
            startActivity(i)
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val row = adapter.items[pos]
            showChatOptions(row)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val all = db.listChats()
        val filtered = if (filter.isBlank()) all else all.filter {
            it.title.lowercase().contains(filter.lowercase()) ||
                db.messages(it.id).any { m -> m.content.lowercase().contains(filter.lowercase()) }
        }
        adapter.items = filtered
        adapter.notifyDataSetChanged()
        empty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openNewChat() {
        val id = db.createChat("New chat", Prefs.defaultPersonaId, Prefs.webResearchDefault)
        val i = Intent(this, ChatActivity::class.java)
        i.putExtra(ChatActivity.EXTRA_CHAT_ID, id)
        startActivity(i)
    }

    private fun showChatOptions(row: ChatRow) {
        val p = Ui.palette(this)
        val items = arrayOf("Rename", "Delete")
        AlertDialog.Builder(this).setTitle(row.title).setItems(items) { _, which ->
            when (which) {
                0 -> {
                    val et = EditText(this)
                    et.setText(row.title)
                    AlertDialog.Builder(this).setTitle("Rename chat").setView(et)
                        .setPositiveButton("Save") { _, _ ->
                            db.updateChat(row.id, title = et.text.toString().trim().ifBlank { row.title })
                            reload()
                        }.setNegativeButton("Cancel", null).show()
                }
                1 -> {
                    AlertDialog.Builder(this).setTitle("Delete chat?").setMessage(row.title)
                        .setPositiveButton("Delete") { _, _ -> db.deleteChat(row.id); reload() }
                        .setNegativeButton("Cancel", null).show()
                }
            }
        }.show()
    }

    private inner class ChatAdapter : BaseAdapter() {
        var items: List<ChatRow> = emptyList()

        override fun getCount() = items.size
        override fun getItem(pos: Int) = items[pos]
        override fun getItemId(pos: Int) = items[pos].id

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_chat, parent, false)
            val row = items[pos]
            val p = Ui.palette(this@MainActivity)
            v.findViewById<TextView>(R.id.title).apply {
                text = row.title
                setTextColor(p.text)
            }
            val last = db.messages(row.id).lastOrNull()
            v.findViewById<TextView>(R.id.preview).apply {
                text = last?.content?.replace("\n", " ")?.let { com.nofilter.ai.util.Text.clamp(it, 70) } ?: "No messages yet"
                setTextColor(p.muted)
            }
            v.findViewById<TextView>(R.id.time).apply {
                text = Ui.timeStr(row.updated)
                setTextColor(p.muted)
            }
            Ui.tint(v.findViewById(R.id.icon), p.accent)
            return v
        }
    }
}
