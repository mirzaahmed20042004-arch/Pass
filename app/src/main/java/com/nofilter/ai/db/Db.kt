package com.nofilter.ai.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class ChatRow(val id: Long, val title: String, val personaId: Long, val web: Boolean, val created: Long, val updated: Long)
data class MessageRow(val id: Long, val chatId: Long, val role: String, val content: String, val sources: String, val experts: String, val ts: Long)
data class MemoryRow(val id: Long, val text: String, val tags: String, val ts: Long, val hits: Int)
data class PersonaRow(val id: Long, val name: String, val emoji: String, val instructions: String, val tone: String, val isCustom: Boolean, val ts: Long)

class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, "nofilter.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chats(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, persona_id INTEGER DEFAULT 0, web INTEGER DEFAULT 1, created INTEGER, updated INTEGER)")
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, chat_id INTEGER, role TEXT, content TEXT, sources TEXT DEFAULT '', experts TEXT DEFAULT '', ts INTEGER)")
        db.execSQL("CREATE TABLE memories(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, tags TEXT DEFAULT '', ts INTEGER, hits INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE personas(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, emoji TEXT DEFAULT '🤖', instructions TEXT DEFAULT '', tone TEXT DEFAULT 'Balanced', is_custom INTEGER DEFAULT 0, ts INTEGER)")
        seedPersonas(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    private data class SeedPersona(val name: String, val emoji: String, val instructions: String, val tone: String)

    private fun seedPersonas(db: SQLiteDatabase) {
        val defaults = listOf(
            SeedPersona("General", "🤖", "You are a helpful, knowledgeable assistant. Answer clearly and directly.", "Balanced"),
            SeedPersona("Boss", "💼", "Be direct, professional and concise. Lead with the answer, then key details. No fluff.", "Formal"),
            SeedPersona("Teacher", "🍎", "Explain step by step like teaching a student. Use simple words, examples and analogies.", "Friendly"),
            SeedPersona("Coder", "💻", "Answer with working code first, then a short explanation. Prefer Kotlin, Python or JavaScript.", "Technical"),
            SeedPersona("Researcher", "🔬", "Give well-structured, sourced answers. Separate facts from opinion. Cite sources.", "Formal"),
            SeedPersona("Creative", "🎨", "Be imaginative and vivid. Offer original ideas, metaphors and story-like framing.", "Playful"),
            SeedPersona("Friend", "😄", "Chat casually and warmly, like a good friend. Use emojis naturally.", "Casual"),
            SeedPersona("Poet", "🌙", "Answer with a poetic touch — rhythm, imagery and beauty in every line.", "Poetic"),
            SeedPersona("ELI5", "🧒", "Explain like I'm five. Super simple words, tiny examples, zero jargon.", "Friendly"),
            SeedPersona("Concise", "⚡", "Answer in the fewest words possible. Bullets only. Maximum 5 lines.", "Concise")
        )
        val now = System.currentTimeMillis()
        for ((name, emoji, instructions, tone) in defaults) {
            val cv = ContentValues()
            cv.put("name", name); cv.put("emoji", emoji); cv.put("instructions", instructions)
            cv.put("tone", tone); cv.put("is_custom", 0); cv.put("ts", now)
            db.insert("personas", null, cv)
        }
    }

    // ---------- chats ----------
    fun listChats(): List<ChatRow> {
        val out = ArrayList<ChatRow>()
        readableDatabase.rawQuery("SELECT * FROM chats ORDER BY updated DESC", null).use { c ->
            while (c.moveToNext()) out.add(chatOf(c))
        }
        return out
    }

    private fun chatOf(c: Cursor) = ChatRow(
        c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1, c.getLong(4), c.getLong(5)
    )

    fun createChat(title: String, personaId: Long, web: Boolean): Long {
        val now = System.currentTimeMillis()
        val cv = ContentValues()
        cv.put("title", title); cv.put("persona_id", personaId)
        cv.put("web", if (web) 1 else 0); cv.put("created", now); cv.put("updated", now)
        return writableDatabase.insert("chats", null, cv)
    }

    fun getChat(id: Long): ChatRow? {
        readableDatabase.rawQuery("SELECT * FROM chats WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) chatOf(c) else null
        }
    }

    fun updateChat(id: Long, title: String? = null, personaId: Long? = null, web: Boolean? = null) {
        val cv = ContentValues()
        if (title != null) cv.put("title", title)
        if (personaId != null) cv.put("persona_id", personaId)
        if (web != null) cv.put("web", if (web) 1 else 0)
        cv.put("updated", System.currentTimeMillis())
        writableDatabase.update("chats", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteChat(id: Long) {
        writableDatabase.delete("messages", "chat_id=?", arrayOf(id.toString()))
        writableDatabase.delete("chats", "id=?", arrayOf(id.toString()))
    }

    // ---------- messages ----------
    fun addMessage(chatId: Long, role: String, content: String, sources: String = "", experts: String = ""): Long {
        val cv = ContentValues()
        cv.put("chat_id", chatId); cv.put("role", role); cv.put("content", content)
        cv.put("sources", sources); cv.put("experts", experts); cv.put("ts", System.currentTimeMillis())
        return writableDatabase.insert("messages", null, cv).also {
            updateChat(chatId)
        }
    }

    fun updateMessage(id: Long, content: String, sources: String, experts: String) {
        val cv = ContentValues()
        cv.put("content", content); cv.put("sources", sources); cv.put("experts", experts)
        writableDatabase.update("messages", cv, "id=?", arrayOf(id.toString()))
    }

    fun messages(chatId: Long): List<MessageRow> {
        val out = ArrayList<MessageRow>()
        readableDatabase.rawQuery("SELECT * FROM messages WHERE chat_id=? ORDER BY ts ASC, id ASC", arrayOf(chatId.toString())).use { c ->
            while (c.moveToNext()) {
                out.add(MessageRow(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6)))
            }
        }
        return out
    }

    fun lastUserMessage(chatId: Long): MessageRow? {
        readableDatabase.rawQuery("SELECT * FROM messages WHERE chat_id=? AND role='user' ORDER BY ts DESC, id DESC LIMIT 1", arrayOf(chatId.toString())).use { c ->
            return if (c.moveToFirst()) MessageRow(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getLong(6)) else null
        }
    }

    // ---------- memories ----------
    fun memories(): List<MemoryRow> {
        val out = ArrayList<MemoryRow>()
        readableDatabase.rawQuery("SELECT * FROM memories ORDER BY ts DESC", null).use { c ->
            while (c.moveToNext()) out.add(memoryOf(c))
        }
        return out
    }

    private fun memoryOf(c: Cursor) = MemoryRow(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4))

    fun addMemory(text: String, tags: String = ""): Long {
        val cv = ContentValues()
        cv.put("text", text); cv.put("tags", tags); cv.put("ts", System.currentTimeMillis()); cv.put("hits", 0)
        return writableDatabase.insert("memories", null, cv)
    }

    fun updateMemory(id: Long, text: String, tags: String) {
        val cv = ContentValues()
        cv.put("text", text); cv.put("tags", tags)
        writableDatabase.update("memories", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteMemory(id: Long) { writableDatabase.delete("memories", "id=?", arrayOf(id.toString())) }

    fun bumpMemory(id: Long) {
        writableDatabase.execSQL("UPDATE memories SET hits=hits+1 WHERE id=?", arrayOf(id.toString()))
    }

    // ---------- personas ----------
    fun personas(): List<PersonaRow> {
        val out = ArrayList<PersonaRow>()
        readableDatabase.rawQuery("SELECT * FROM personas ORDER BY is_custom ASC, id ASC", null).use { c ->
            while (c.moveToNext()) out.add(personaOf(c))
        }
        return out
    }

    private fun personaOf(c: Cursor) = PersonaRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5) == 1, c.getLong(6))

    fun persona(id: Long): PersonaRow? {
        readableDatabase.rawQuery("SELECT * FROM personas WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) personaOf(c) else null
        }
    }

    fun addPersona(name: String, emoji: String, instructions: String, tone: String, isCustom: Boolean = true): Long {
        val cv = ContentValues()
        cv.put("name", name); cv.put("emoji", emoji); cv.put("instructions", instructions)
        cv.put("tone", tone); cv.put("is_custom", if (isCustom) 1 else 0); cv.put("ts", System.currentTimeMillis())
        return writableDatabase.insert("personas", null, cv)
    }

    fun updatePersona(id: Long, name: String, emoji: String, instructions: String, tone: String) {
        val cv = ContentValues()
        cv.put("name", name); cv.put("emoji", emoji); cv.put("instructions", instructions); cv.put("tone", tone)
        writableDatabase.update("personas", cv, "id=?", arrayOf(id.toString()))
    }

    fun deletePersona(id: Long) {
        writableDatabase.delete("personas", "id=?", arrayOf(id.toString()))
        writableDatabase.execSQL("UPDATE chats SET persona_id=0 WHERE persona_id=?", arrayOf(id.toString()))
    }

    // ---------- export / import ----------
    fun exportAll(): String {
        val root = JSONObject()
        val chats = JSONArray()
        for (ch in listChats()) {
            val o = JSONObject()
            o.put("id", ch.id); o.put("title", ch.title); o.put("persona_id", ch.personaId)
            o.put("web", ch.web); o.put("created", ch.created); o.put("updated", ch.updated)
            val msgs = JSONArray()
            for (m in messages(ch.id)) {
                val mo = JSONObject()
                mo.put("role", m.role); mo.put("content", m.content); mo.put("sources", m.sources)
                mo.put("experts", m.experts); mo.put("ts", m.ts)
                msgs.put(mo)
            }
            o.put("messages", msgs)
            chats.put(o)
        }
        val mems = JSONArray()
        for (m in memories()) {
            val o = JSONObject()
            o.put("text", m.text); o.put("tags", m.tags); o.put("ts", m.ts)
            mems.put(o)
        }
        val pers = JSONArray()
        for (p in personas().filter { it.isCustom }) {
            val o = JSONObject()
            o.put("name", p.name); o.put("emoji", p.emoji); o.put("instructions", p.instructions); o.put("tone", p.tone)
            pers.put(o)
        }
        root.put("chats", chats); root.put("memories", mems); root.put("personas", pers)
        root.put("exported_at", System.currentTimeMillis())
        return root.toString(2)
    }

    fun importAll(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            root.optJSONArray("memories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    addMemory(o.optString("text"), o.optString("tags"))
                }
            }
            root.optJSONArray("personas")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    addPersona(o.optString("name"), o.optString("emoji", "🤖"), o.optString("instructions"), o.optString("tone", "Balanced"))
                }
            }
            root.optJSONArray("chats")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = createChat(o.optString("title", "Imported chat"), o.optLong("persona_id", 0), o.optBoolean("web", true))
                    o.optJSONArray("messages")?.let { msgs ->
                        for (j in 0 until msgs.length()) {
                            val m = msgs.getJSONObject(j)
                            addMessage(id, m.optString("role", "user"), m.optString("content"), m.optString("sources"), m.optString("experts"))
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM messages")
        writableDatabase.execSQL("DELETE FROM chats")
        writableDatabase.execSQL("DELETE FROM memories")
        writableDatabase.execSQL("DELETE FROM personas WHERE is_custom=1")
    }
}
