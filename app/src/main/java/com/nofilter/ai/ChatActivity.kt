package com.nofilter.ai

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.nofilter.ai.db.Db
import com.nofilter.ai.db.MessageRow
import com.nofilter.ai.engine.Ensemble
import com.nofilter.ai.engine.WebResearch.Source
import com.nofilter.ai.ui.Ui
import com.nofilter.ai.util.Markdown
import com.nofilter.ai.util.Prefs
import com.nofilter.ai.util.Speech
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChatActivity : BaseActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        private const val REQ_PICK_IMAGE = 9101
        private const val REQ_OCR = 9102
        private const val REQ_AUDIO = 9103
    }

    private lateinit var db: Db
    private var chatId: Long = -1
    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var typingStatus: TextView
    private lateinit var typingRow: View
    private lateinit var titleView: TextView
    private lateinit var personaView: TextView
    private lateinit var btnWeb: ImageButton
    private val adapter = MsgAdapter()
    private var streaming = false
    private var streamText = StringBuilder()
    private data class MsgRow(var msg: MessageRow)
    private var streamRow: MsgRow? = null
    private lateinit var tts: Speech.Tts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        db = Db(this)
        Prefs.init(this)
        tts = Speech.Tts(this)
        tts.init()

        chatId = intent.getLongExtra(EXTRA_CHAT_ID, -1)
        if (chatId < 0) chatId = db.createChat("New chat", Prefs.defaultPersonaId, Prefs.webResearchDefault)

        val p = Ui.palette(this)
        list = findViewById(R.id.message_list)
        input = findViewById(R.id.input)
        typingRow = findViewById(R.id.typing_row)
        typingStatus = findViewById(R.id.typing_status)
        titleView = findViewById(R.id.chat_title)
        personaView = findViewById(R.id.chat_persona)
        btnWeb = findViewById(R.id.btn_web)
        list.adapter = adapter

        input.background = Ui.inputBackground(p)

        Ui.tint(findViewById(R.id.btn_back), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_sources), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_persona), p.text)
        Ui.tint(findViewById<ImageButton>(R.id.btn_attach), p.accent2)
        Ui.tint(findViewById<ImageButton>(R.id.btn_mic), p.accent2)
        Ui.tint(findViewById<ImageButton>(R.id.btn_send), android.graphics.Color.WHITE)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_send).setOnClickListener { send() }
        findViewById<ImageButton>(R.id.btn_attach).setOnClickListener { pickImage() }
        findViewById<ImageButton>(R.id.btn_mic).setOnClickListener { startMic() }
        findViewById<ImageButton>(R.id.btn_persona).setOnClickListener { showPersonaPicker() }
        btnWeb.setOnClickListener { toggleWeb() }
        findViewById<ImageButton>(R.id.btn_sources).setOnClickListener { showLastSources() }
        findViewById<ImageButton>(R.id.btn_sources).setOnLongClickListener { showChatTools(); true }
        titleView.setOnClickListener { showChatTools() }
        personaView.setOnClickListener { showPersonaPicker() }

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        refreshHeader()
        reloadMessages()
    }

    private fun refreshHeader() {
        val chat = db.getChat(chatId) ?: return
        val p = Ui.palette(this)
        titleView.text = chat.title
        val persona = db.persona(chat.personaId)
        personaView.text = buildString {
            append(if (persona != null) "${persona.emoji} ${persona.name}" else "🤖 General")
            append(" • ")
            append(if (chat.web && !Prefs.offlineMode) "🌐 web research ON" else "📴 offline")
        }
        Ui.tint(btnWeb, if (chat.web && !Prefs.offlineMode) p.accent2 else p.muted)
    }

    private fun reloadMessages() {
        adapter.rows = db.messages(chatId).map { MsgRow(it) }
        adapter.notifyDataSetChanged()
        if (adapter.rows.isNotEmpty()) list.setSelection(adapter.rows.size - 1)
    }

    private fun send() {
        if (streaming) { Ui.toast(this, "Still thinking…"); return }
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.text.clear()
        val chat = db.getChat(chatId) ?: return
        if (chat.title == "New chat") {
            db.updateChat(chatId, title = text.take(48))
            refreshHeader()
        }
        db.addMessage(chatId, "user", text)
        reloadMessages()
        ask(text, replaceAiId = -1L)
    }

    private fun ask(query: String, replaceAiId: Long = -1L) {
        val chat = db.getChat(chatId) ?: return
        val persona = db.persona(chat.personaId)
        streaming = true
        streamText = StringBuilder()
        streamRow = MsgRow(MessageRow(-1, chatId, "ai", "", "", "", System.currentTimeMillis()))
        adapter.rows = adapter.rows + streamRow!!
        adapter.notifyDataSetChanged()
        list.setSelection(adapter.rows.size - 1)
        Ui.show(typingRow)

        Ensemble.answer(this, db, query, chat.web, persona, object : Ensemble.Listener {
            override fun onStatus(s: String) {
                if (s.isNotBlank()) {
                    typingStatus.text = s
                    streamRow?.msg = streamRow?.msg?.copy(content = streamText.toString()) ?: return
                    adapter.notifyDataSetChanged()
                    list.setSelection(adapter.rows.size - 1)
                }
            }

            override fun onChunk(s: String) {
                streamText.append(s)
                streamRow?.msg = streamRow?.msg?.copy(content = streamText.toString()) ?: return
                adapter.notifyDataSetChanged()
                list.setSelection(adapter.rows.size - 1)
            }

            override fun onDone(finalText: String, sources: List<Source>, experts: String) {
                streaming = false
                Ui.hide(typingRow)
                val srcJson = JSONArray().apply {
                    for (s in sources) put(JSONObject().put("title", s.title).put("url", s.url).put("snippet", s.snippet))
                }.toString()
                if (replaceAiId > 0) {
                    db.updateMessage(replaceAiId, finalText, srcJson, experts)
                } else {
                    db.addMessage(chatId, "ai", finalText, srcJson, experts)
                }
                reloadMessages()
            }

            override fun onError(msg: String) {
                streaming = false
                Ui.hide(typingRow)
                db.addMessage(chatId, "ai", "⚠️ $msg", "", "")
                reloadMessages()
            }
        })
    }

    private fun showChatTools() {
        val options = arrayOf(
            "⚡ Starter prompts",
            "✏️ Rename chat",
            "📤 Share chat as Markdown",
            "🔗 View last answer's sources"
        )
        AlertDialog.Builder(this).setTitle("Chat Tools").setItems(options) { _, which ->
            when (which) {
                0 -> showStarterPrompts()
                1 -> renameChat()
                2 -> shareChatMarkdown()
                3 -> showLastSources()
            }
        }.show()
    }

    private fun showStarterPrompts() {
        val prompts = arrayOf(
            "How do Transformer LLMs and Mixture-of-Experts work?",
            "sqrt(144) + 18% of 250",
            "Convert 98.6 f to c",
            "Write a Python binary search function",
            "Roadmap to learn Kotlin Android development",
            "/help"
        )
        AlertDialog.Builder(this).setTitle("Try a prompt")
            .setItems(prompts) { _, which ->
                input.setText(prompts[which])
                input.setSelection(input.text.length)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun shareChatMarkdown() {
        val chat = db.getChat(chatId) ?: return
        val msgs = db.messages(chatId)
        if (msgs.isEmpty()) { Ui.toast(this, "Chat is empty"); return }
        val md = buildString {
            append("# ").append(chat.title).append("\n\n")
            for (m in msgs) {
                append(if (m.role == "user") "**You:**\n" else "**NoFilter Ai (${m.experts}):**\n")
                append(m.content).append("\n\n---\n\n")
            }
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, chat.title)
        i.putExtra(Intent.EXTRA_TEXT, md)
        startActivity(Intent.createChooser(i, "Share chat Markdown"))
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(Intent.createChooser(intent, "Select image for offline OCR"), REQ_PICK_IMAGE)
    }

    private fun startMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        } else {
            Speech.startStt(this)
        }
    }

    private fun toggleWeb() {
        val chat = db.getChat(chatId) ?: return
        val next = !chat.web
        db.updateChat(chatId, web = next)
        refreshHeader()
        Ui.toast(this, if (next) "Web research ON 🌐" else "Web research OFF 📴")
    }

    private fun showPersonaPicker() {
        val personas = db.personas()
        val names = personas.map { "${it.emoji} ${it.name} — ${it.tone}" }.toTypedArray()
        val chat = db.getChat(chatId)
        val checked = personas.indexOfFirst { it.id == chat?.personaId }.coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Choose persona (how NoFilter Ai behaves)")
            .setSingleChoiceItems(names, checked) { d, which ->
                db.updateChat(chatId, personaId = personas[which].id)
                Prefs.defaultPersonaId = personas[which].id
                refreshHeader()
                d.dismiss()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showLastSources() {
        val lastAi = db.messages(chatId).lastOrNull { it.role == "ai" } ?: run {
            Ui.toast(this, "No AI answer yet"); return
        }
        showSourcesDialog(parseSources(lastAi.sources))
    }

    private fun parseSources(json: String): List<Source> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Source(o.optString("title"), o.optString("url"), o.optString("snippet"))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun showSourcesDialog(sources: List<Source>) {
        if (sources.isEmpty()) { Ui.toast(this, "No external web sources for this answer"); return }
        val items = sources.mapIndexed { idx, s -> "[${idx + 1}] ${s.title}\n${s.url}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Web Sources (${sources.size})")
            .setItems(items) { _, which ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sources[which].url)))
                } catch (e: Exception) { Ui.toast(this, "Cannot open link") }
            }.setNegativeButton("Close", null).show()
    }

    private fun renameChat() {
        val chat = db.getChat(chatId) ?: return
        val et = EditText(this)
        et.setText(chat.title)
        AlertDialog.Builder(this).setTitle("Rename chat").setView(et)
            .setPositiveButton("Save") { _, _ ->
                db.updateChat(chatId, title = et.text.toString().trim().ifBlank { chat.title })
                refreshHeader()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun onMessageLongClick(msg: MessageRow) {
        val isAi = msg.role == "ai"
        val options = ArrayList<String>()
        options.add("Copy")
        options.add("Share")
        if (isAi) {
            options.add("🔊 Read aloud")
            options.add("⭐ Memorize this")
            options.add("🔄 Regenerate answer")
            options.add("🔗 View sources")
        } else {
            options.add("⭐ Memorize this")
        }
        AlertDialog.Builder(this).setTitle(if (isAi) "AI Answer" else "Your Message")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Copy" -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("NoFilter Ai", msg.content))
                        Ui.toast(this, "Copied")
                    }
                    "Share" -> {
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_TEXT, msg.content)
                        startActivity(Intent.createChooser(i, "Share via"))
                    }
                    "🔊 Read aloud" -> tts.speak(msg.content)
                    "⭐ Memorize this" -> {
                        db.addMemory(msg.content.take(500), "chat:$chatId")
                        Ui.toast(this, "Saved to memory 🧠")
                    }
                    "🔄 Regenerate answer" -> {
                        val lastUser = db.lastUserMessage(chatId)
                        if (lastUser != null) ask(lastUser.content, replaceAiId = msg.id)
                    }
                    "🔗 View sources" -> showSourcesDialog(parseSources(msg.sources))
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data!!
            try {
                val tmp = File(cacheDir, "attach_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
                val i = Intent(this, OcrActivity::class.java)
                i.putExtra(OcrActivity.EXTRA_IMAGE_PATH, tmp.absolutePath)
                startActivityForResult(i, REQ_OCR)
            } catch (e: Exception) {
                Ui.toast(this, "Could not read image: ${e.message}")
            }
        } else if (requestCode == REQ_OCR && resultCode == RESULT_OK && data != null) {
            val text = data.getStringExtra(OcrActivity.EXTRA_RESULT_TEXT) ?: return
            val current = input.text.toString()
            input.setText((if (current.isBlank()) "" else "$current\n") + "📷 Image text:\n" + text)
            input.setSelection(input.text.length)
        } else if (requestCode == Speech.REQ_STT && resultCode == RESULT_OK) {
            val heard = Speech.sttResult(data)
            if (heard.isNotBlank()) {
                val current = input.text.toString()
                input.setText(if (current.isBlank()) heard else "$current $heard")
                input.setSelection(input.text.length)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Speech.startStt(this)
        } else if (requestCode == REQ_AUDIO) {
            Ui.toast(this, "Microphone permission needed for voice input")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }

    private inner class MsgAdapter : BaseAdapter() {
        var rows: List<MsgRow> = emptyList()

        override fun getCount() = rows.size
        override fun getItem(pos: Int) = rows[pos]
        override fun getItemId(pos: Int) = rows[pos].msg.id

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_message, parent, false)
            val row = rows[pos]
            val m = row.msg
            val p = Ui.palette(this@ChatActivity)
            val bubble = v.findViewById<TextView>(R.id.bubble)
            val meta = v.findViewById<TextView>(R.id.meta)
            val container = v as LinearLayout

            val isUser = m.role == "user"
            container.gravity = if (isUser) Gravity.END else Gravity.START
            bubble.background = Ui.bubble(p, isUser)
            bubble.maxWidth = Ui.dp(this@ChatActivity, 305f)

            if (isUser) {
                bubble.setTextColor(android.graphics.Color.WHITE)
                bubble.text = m.content
            } else {
                bubble.setTextColor(p.text)
                val scale = Prefs.fontScale
                val sp = 15f * scale
                bubble.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp)
                bubble.text = Markdown.render(m.content, p, (sp * resources.displayMetrics.scaledDensity).toInt())
                bubble.movementMethod = LinkMovementMethod.getInstance()
            }
            bubble.setOnLongClickListener {
                if (m.id > 0) onMessageLongClick(m)
                true
            }
            if (!isUser && (m.experts.isNotBlank() || m.ts > 0)) {
                meta.visibility = View.VISIBLE
                meta.setTextColor(p.muted)
                meta.text = buildString {
                    if (m.experts.isNotBlank()) append(m.experts).append("  ")
                    append(Ui.timeStr(m.ts))
                }
                (meta.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.START
            } else {
                meta.visibility = View.GONE
            }
            return v
        }
    }
}
