package com.haira.ai

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private data class ChatMessage(val text: String, val fromUser: Boolean, val time: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(Locale("en", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale.getDefault())
            }
        }
        setContent { HairaApp(::loadMessages, ::saveMessages, ::speakReply, ::clearMessages) }
    }

    private fun loadMessages(): List<ChatMessage> {
        val raw = getSharedPreferences("haira_chat", MODE_PRIVATE).getString("messages", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                ChatMessage(item.getString("text"), item.getBoolean("fromUser"), item.optLong("time"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveMessages(messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.takeLast(150).forEach { array.put(JSONObject().put("text", it.text).put("fromUser", it.fromUser).put("time", it.time)) }
        getSharedPreferences("haira_chat", MODE_PRIVATE).edit().putString("messages", array.toString()).apply()
    }

    private fun clearMessages() { getSharedPreferences("haira_chat", MODE_PRIVATE).edit().remove("messages").apply() }
    private fun speakReply(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haira_reply") }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
private fun HairaApp(
    loadMessages: () -> List<ChatMessage>,
    saveMessages: (List<ChatMessage>) -> Unit,
    speak: (String) -> Unit,
    clearMessages: () -> Unit
) {
    val cyan = Color(0xFF35D9F2)
    var messages by remember { mutableStateOf(loadMessages()) }
    var draft by remember { mutableStateOf("") }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val updated = (messages + ChatMessage(text, true) + ChatMessage(localReply(text), false)).takeLast(150)
        messages = updated
        saveMessages(updated)
        draft = ""
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = cyan, background = Color(0xFF050A12), surface = Color(0xFF101C2A))) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10283A), Color(0xFF050A12)))).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("HAIRA", color = cyan, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("PERSONAL AI ASSISTANT", color = Color.LightGray, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                TextButton(onClick = { messages = emptyList(); clearMessages() }) { Text("CLEAR", color = Color(0xFFFF8A8A), fontSize = 11.sp) }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFF102333), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("CHAT FOUNDATION • v0.2.0", color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Messages are saved on this phone. Replies are local starter responses; the AI engine is not connected yet.", color = Color(0xFFB8CBD8), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (messages.isEmpty()) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(88.dp).background(Color(0x2235D9F2), CircleShape), contentAlignment = Alignment.Center) { Text("H", color = cyan, fontSize = 48.sp, fontWeight = FontWeight.Light) }
                    Spacer(Modifier.height(18.dp))
                    Text("Hello, Sir ji", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your chat space is ready.", color = Color(0xFF9FB5C4), fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages) { message ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (message.fromUser) Color(0xFF16445A) else Color(0xFF142333), shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(if (message.fromUser) "YOU" else "HAIRA", color = if (message.fromUser) Color(0xFF9ADCF0) else cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(message.text, color = Color.White, fontSize = 14.sp)
                                    if (!message.fromUser) TextButton(onClick = { speak(message.text) }, contentPadding = PaddingValues(0.dp)) { Text("▶ SPEAK", color = cyan, fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Message HAIRA...", color = Color(0xFF78909F)) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = cyan, unfocusedBorderColor = Color(0xFF355064), cursorColor = cyan),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { sendMessage() }, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(52.dp)) { Text("➤", fontSize = 22.sp) }
            }
        }
    }
}

private fun localReply(input: String): String {
    val text = input.lowercase(Locale.ROOT)
    return when {
        Regex(".*\\b(hi|hello|hey)\\b.*").matches(text) -> "Hello, Sir ji. I am here. My chat foundation is active, while the full AI engine is still being integrated."
        text.contains("your name") || text.contains("who are you") -> "I am HAIRA, your Android personal assistant project. My full AI capabilities are not connected in this build yet."
        text.contains("what can you do") || text.contains("help") -> "This build can save conversations on your phone and read HAIRA replies aloud using your device's speech engine."
        text.contains("time") -> "Your phone's current time is " + java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date()) + "."
        else -> "I have saved your message on this phone. My full AI reasoning engine is not connected yet, so I cannot answer this properly in the current build."
    }
}
