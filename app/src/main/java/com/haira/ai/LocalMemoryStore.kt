package com.haira.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HairaMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val time: Long = System.currentTimeMillis()
)

data class HairaMemory(
    val id: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** Small, private, device-local store for chat history and user-pinned memories. */
class LocalMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("haira_local_memory_v1", Context.MODE_PRIVATE)

    fun loadMessages(): List<HairaMessage> = runCatching {
        val array = JSONArray(prefs.getString("messages", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            HairaMessage(
                id = item.optLong("id", index.toLong()),
                text = item.optString("text"),
                fromUser = item.optBoolean("fromUser"),
                time = item.optLong("time", System.currentTimeMillis())
            )
        }
    }.getOrDefault(emptyList())

    fun saveMessages(messages: List<HairaMessage>) {
        val array = JSONArray()
        messages.takeLast(500).forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("text", message.text)
                    .put("fromUser", message.fromUser)
                    .put("time", message.time)
            )
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    fun loadMemories(): List<HairaMemory> = runCatching {
        val array = JSONArray(prefs.getString("pinned_memories", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            HairaMemory(item.optLong("id", index.toLong()), item.optString("text"), item.optLong("createdAt"))
        }
    }.getOrDefault(emptyList())

    fun saveMemories(memories: List<HairaMemory>) {
        val array = JSONArray()
        memories.takeLast(300).forEach { memory ->
            array.put(JSONObject().put("id", memory.id).put("text", memory.text).put("createdAt", memory.createdAt))
        }
        prefs.edit().putString("pinned_memories", array.toString()).apply()
    }

    fun clearMessages() { prefs.edit().remove("messages").apply() }
    fun clearAllLocalMemory() { prefs.edit().clear().apply() }
}
