package com.haira.ai

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatMessage(val text: String, val fromUser: Boolean, val time: Long = System.currentTimeMillis())

private val Bg = Color(0xFF050912)
private val Panel = Color(0xFF0D1826)
private val Panel2 = Color(0xFF122437)
private val Cyan = Color(0xFF55E6F2)
private val Muted = Color(0xFF8CA4B8)
private val Gold = Color(0xFFD7B56D)
private const val HAIRA_MODEL_FILE = "Qwen3-0.6B.litertlm"
private const val HAIRA_MODEL_URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm"
private const val HAIRA_MODEL_MIN_BYTES = 500_000_000L

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var localAi: LocalAiEngine
    private val modelFile: File get() = File(getExternalFilesDir(null), HAIRA_MODEL_FILE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localAi = LocalAiEngine(this)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val result = tts?.setLanguage(Locale("en", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }
        }
        setContent {
            HairaApp(
                loadMessages = ::loadMessages,
                saveMessages = ::saveMessages,
                speak = ::speakReply,
                clearMessages = ::clearMessages,
                modelExists = ::modelExists,
                downloadModel = ::downloadModel,
                loadModel = ::loadModel,
                sendPrompt = { prompt, chunk, done, error -> localAi.send(prompt, chunk, done, error) },
                handleCommand = ::handleCommand
            )
        }
    }

    private fun modelExists(): Boolean = modelFile.exists() && modelFile.length() >= HAIRA_MODEL_MIN_BYTES

    private fun downloadModel() {
        try {
            val request = DownloadManager.Request(Uri.parse(HAIRA_MODEL_URL))
                .setTitle("HAIRA local AI model")
                .setDescription("Downloading Qwen3 0.6B model (about 586 MB)")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(this, null, HAIRA_MODEL_FILE)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        } catch (_: Exception) { }
    }

    private fun loadModel(onReady: () -> Unit, onError: (String) -> Unit) {
        if (!modelExists()) {
            onError("Model file is missing or incomplete. Download it first.")
            return
        }
        localAi.load(modelFile.absolutePath, onReady, onError)
    }

    private fun handleCommand(raw: String): String? {
        val command = raw.lowercase(Locale.ROOT).trim()
        return try {
            when {
                command == "open youtube" || command == "youtube kholo" || command == "youtube open karo" -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                    "Opening YouTube."
                }
                command == "open settings" || command == "settings kholo" || command == "phone settings kholo" -> {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    "Opening Android Settings."
                }
                command == "open camera" || command == "camera kholo" || command == "camera open karo" -> {
                    startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    "Opening the camera. The selected text model cannot understand images yet."
                }
                command.startsWith("remind me to ") || command.startsWith("remind me ") || command.startsWith("mujhe yaad dilana ") -> {
                    val title = raw.substringAfter("to ", raw.substringAfter("me ", raw)).trim().ifBlank { "HAIRA reminder" }
                    val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, title)
                        .putExtra(CalendarContract.Events.DESCRIPTION, "Created from HAIRA AI")
                    startActivity(intent)
                    "I opened your Calendar event form. Choose the date and time, then save it."
                }
                else -> null
            }
        } catch (_: Exception) {
            "I couldn't open that action. Check that a compatible app is installed."
        }
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
        messages.takeLast(300).forEach {
            array.put(JSONObject().put("text", it.text).put("fromUser", it.fromUser).put("time", it.time))
        }
        getSharedPreferences("haira_chat", MODE_PRIVATE).edit().putString("messages", array.toString()).apply()
    }

    private fun clearMessages() {
        getSharedPreferences("haira_chat", MODE_PRIVATE).edit().remove("messages").apply()
    }

    private fun speakReply(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haira_reply")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        if (::localAi.isInitialized) localAi.close()
        super.onDestroy()
    }
}

@Composable
private fun HairaApp(
    loadMessages: () -> List<ChatMessage>,
    saveMessages: (List<ChatMessage>) -> Unit,
    speak: (String) -> Unit,
    clearMessages: () -> Unit,
    modelExists: () -> Boolean,
    downloadModel: () -> Unit,
    loadModel: ((() -> Unit), ((String) -> Unit)) -> Unit,
    sendPrompt: (String, (String) -> Unit, () -> Unit, (String) -> Unit) -> Unit,
    handleCommand: (String) -> String?
) {
    var messages by remember { mutableStateOf(loadMessages()) }
    var selectedTab by remember { mutableStateOf("HOME") }
    var draft by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(modelExists()) }
    var aiLoaded by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf(if (modelReady) "Model file found on this device" else "Model not downloaded") }
    var busy by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) draft = listOf(draft.trim(), spoken).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return
        messages = (messages + ChatMessage(text, true)).takeLast(300)
        saveMessages(messages)
        draft = ""

        val commandReply = handleCommand(text)
        if (commandReply != null) {
            messages = (messages + ChatMessage(commandReply, false)).takeLast(300)
            saveMessages(messages)
            return
        }
        if (!aiLoaded) {
            val reply = if (modelExists()) "The local model is downloaded but not loaded. Open Settings and tap Load Model."
                else "Local AI is not set up yet. Open Settings → AI Model and download the optional model. No API key is required."
            messages = (messages + ChatMessage(reply, false)).takeLast(300)
            saveMessages(messages)
            return
        }
        messages = (messages + ChatMessage("", false)).takeLast(300)
        busy = true
        sendPrompt(
            text,
            { chunk ->
                val last = messages.lastOrNull()
                if (last != null && !last.fromUser) {
                    messages = messages.dropLast(1) + last.copy(text = last.text + chunk)
                    saveMessages(messages)
                }
            },
            { busy = false },
            { error ->
                busy = false
                val last = messages.lastOrNull()
                if (last != null && !last.fromUser) {
                    messages = messages.dropLast(1) + last.copy(text = "AI error: $error")
                    saveMessages(messages)
                }
            }
        )
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF09121E), contentColor = Cyan) {
                    listOf("HOME", "CHAT", "MEMORY", "SETTINGS").forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(when (tab) { "HOME" -> "⌂"; "CHAT" -> "◌"; "MEMORY" -> "▤"; else -> "⚙" }, fontSize = 20.sp) },
                            label = { Text(tab, fontSize = 9.sp, letterSpacing = 1.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0xFF173044), unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0C1D2B), Bg, Color(0xFF070B13))))
                    .padding(horizontal = 18.dp)
            ) {
                Header()
                when (selectedTab) {
                    "HOME" -> HomeScreen(messages.size, aiLoaded, modelReady, onChat = { selectedTab = "CHAT" })
                    "CHAT" -> ChatScreen(messages, draft, { draft = it }, { sendMessage() }, {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to HAIRA")
                        }
                        try { speechLauncher.launch(intent) } catch (_: Exception) { }
                    }, speak)
                    "MEMORY" -> MemoryScreen(messages, search, { search = it })
                    else -> SettingsScreen(
                        messages.size, modelReady, aiLoaded, modelStatus,
                        onRefresh = {
                            modelReady = modelExists()
                            modelStatus = if (modelReady) "Model file found on this device" else "Model not downloaded or download incomplete"
                        },
                        onDownload = {
                            downloadModel()
                            modelStatus = "Download started. Android will notify when it finishes."
                        },
                        onLoad = {
                            modelStatus = "Loading local model..."
                            loadModel(
                                { aiLoaded = true; modelStatus = "Local AI model loaded and ready" },
                                { error -> aiLoaded = false; modelStatus = "Load failed: $error" }
                            )
                        },
                        onClear = { messages = emptyList(); clearMessages() }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("HAIRA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text("PERSONAL INTELLIGENCE SYSTEM", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("LOCAL-FIRST", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("BUILD 0.3.0", color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun HomeScreen(messageCount: Int, aiLoaded: Boolean, modelReady: Boolean, onChat: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Good to see you, Sir ji.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your personal command space.", color = Muted, fontSize = 12.sp)
                }
                Surface(color = Color(0xFF17372F), shape = RoundedCornerShape(20.dp)) {
                    Text("● SYSTEM READY", color = Color(0xFF8BE0B0), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
        item { HoloCore() }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI CORE", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (aiLoaded) "ONLINE" else if (modelReady) "DOWNLOADED" else "SETUP REQUIRED", color = if (aiLoaded) Color(0xFF8BE0B0) else Color(0xFFFFC477), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(when { aiLoaded -> "The local language model is loaded and generating replies on-device."; modelReady -> "The model file is ready. Load it from Settings to start offline AI."; else -> "Download the optional local model from Settings. No API key is required." }, color = Color(0xFFC0CFDA), fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("OPEN HAIRA CHAT  →", color = Cyan, letterSpacing = 1.sp)
                    }
                }
            }
        }
        item {
            Text("SYSTEM OVERVIEW", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard("CHAT MEMORY", messageCount.toString(), "messages saved", Modifier.weight(1f))
                StatusCard("VOICE OUTPUT", "READY", "device TTS", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard("VOICE INPUT", "BASIC", "Android recognizer", Modifier.weight(1f))
                StatusCard("AI REASONING", if (aiLoaded) "READY" else if (modelReady) "LOAD" else "SETUP", "local model", Modifier.weight(1f))
            }
        }
        item {
            Text("QUICK ACCESS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Surface(color = Panel2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onChat() }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("◉", color = Cyan, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Start a conversation", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Text chat and Android speech input", color = Muted, fontSize = 11.sp)
                    }
                    Text("›", color = Cyan, fontSize = 26.sp)
                }
            }
        }
    }
}

@Composable
private fun HoloCore() {
    Box(
        Modifier.fillMaxWidth().height(245.dp)
            .background(Brush.radialGradient(listOf(Color(0xFF163D50), Color(0xFF0A1928), Color.Transparent)), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(218.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Cyan.copy(alpha = 0.08f), radius = size.minDimension * 0.48f, center = center)
            drawCircle(Cyan.copy(alpha = 0.22f), radius = size.minDimension * 0.43f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            drawCircle(Gold.copy(alpha = 0.55f), radius = size.minDimension * 0.35f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            drawCircle(Cyan.copy(alpha = 0.45f), radius = size.minDimension * 0.25f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawCircle(Color(0xFF102E40), radius = size.minDimension * 0.20f, center = center)
            drawCircle(Cyan.copy(alpha = 0.18f), radius = size.minDimension * 0.13f, center = center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("H", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
            Text("HAIRA CORE", color = Cyan, fontSize = 9.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Text("INTERFACE ONLINE", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
        }
        Text("✦", color = Gold, fontSize = 18.sp, modifier = Modifier.offset(x = 83.dp, y = (-76).dp))
        Text("✧", color = Cyan, fontSize = 16.sp, modifier = Modifier.offset(x = (-91).dp, y = 58.dp))
    }
}

@Composable
private fun StatusCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(value, color = if (value == "PENDING") Color(0xFFFFC477) else Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    speak: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text("CONVERSATION", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Saved locally on this device", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◉", color = Cyan, fontSize = 42.sp)
                    Text("Hello, Sir ji", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Start a new conversation.", color = Muted, fontSize = 12.sp)
                    Text("AI model is not connected in this build.", color = Color(0xFFFFC477), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(
                            color = if (message.fromUser) Color(0xFF17465A) else Panel,
                            shape = RoundedCornerShape(17.dp),
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                                Text(if (message.fromUser) "SIR JI" else "HAIRA", color = if (message.fromUser) Color(0xFFB7E9F2) else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(message.text, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                                if (!message.fromUser) TextButton(onClick = { speak(message.text) }, contentPadding = PaddingValues(0.dp)) {
                                    Text("▶  PLAY VOICE", color = Cyan, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(18.dp)).background(Panel).padding(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = Brush.linearGradient(listOf(Cyan, Gold)),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) Text("Message HAIRA...", color = Muted, fontSize = 13.sp)
                        inner()
                    }
                }
            )
            IconButton(onClick = onMic) { Text("🎙", fontSize = 18.sp) }
            Button(onClick = onSend, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp), shape = RoundedCornerShape(13.dp)) {
                Text("SEND", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MemoryScreen(messages: List<ChatMessage>, search: String, onSearch: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("MEMORY VAULT", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Local conversation history", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
        OutlinedTextField(
            value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search saved messages...", color = Muted) },
            singleLine = true, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF294052), cursorColor = Cyan)
        )
        val filtered = messages.filter { it.text.contains(search, ignoreCase = true) }.asReversed()
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (messages.isEmpty()) "No saved conversations yet." else "No matching messages.", color = Muted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { item ->
                    Surface(color = Panel, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(13.dp)) {
                            Text(if (item.fromUser) "YOU" else "HAIRA", color = if (item.fromUser) Gold else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(item.text, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
                            Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.time)), color = Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    messageCount: Int,
    modelReady: Boolean,
    aiLoaded: Boolean,
    modelStatus: String,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SYSTEM SETTINGS", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Privacy and build information", color = Muted, fontSize = 11.sp)
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("ON-DEVICE AI MODEL", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Qwen3 0.6B • about 586 MB • local inference • no API key", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text(modelStatus, color = if (aiLoaded) Color(0xFF8BE0B0) else Color(0xFFFFC477), fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                Text("The model is a separate download, not part of the APK. Download only on Wi-Fi if you want to save mobile data. A compact model may be less capable than cloud AI.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp, bottom = 12.dp))
                Button(onClick = onDownload, enabled = !modelReady, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                    Text(if (modelReady) "MODEL FILE FOUND" else "DOWNLOAD LOCAL MODEL")
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                    Text("CHECK DOWNLOAD")
                }
                OutlinedButton(onClick = onLoad, enabled = modelReady && !aiLoaded, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                    Text(if (aiLoaded) "AI MODEL LOADED" else "LOAD MODEL INTO MEMORY")
                }
            }
        }
        SettingRow("Conversation storage", "Last 300 messages on this device")
        SettingRow("Voice output", "Android TextToSpeech")
        SettingRow("Voice input", "Android speech recognizer; internet may be required")
        SettingRow("Phone commands", "Open YouTube, Android Settings and Camera; reminders open Calendar form")
        SettingRow("Image understanding", "Not included in this text-only model")
        SettingRow("Saved messages", messageCount.toString())
        Spacer(Modifier.height(8.dp))
        Surface(color = Color(0xFF29191B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp)) {
                Text("CLEAR LOCAL CHAT", color = Color(0xFFFF9B9B), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("This permanently removes saved conversation history from this app.", color = Color(0xFFD7B7B7), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                    Text("CLEAR ALL MESSAGES", color = Color(0xFFFF9B9B), fontSize = 10.sp)
                }
            }
        }
        Text("HAIRA AI • ANDROID BUILD 0.4.0", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    Surface(color = Panel, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
