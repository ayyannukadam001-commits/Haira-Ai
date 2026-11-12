package com.haira.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF050912)
private val Panel = Color(0xFF0D1826)
private val Cyan = Color(0xFF55E6F2)
private val Muted = Color(0xFF8CA4B8)
private val Gold = Color(0xFFD7B56D)

class MainActivity : ComponentActivity() {
    private lateinit var localModel: LiteRtLocalModelEngine
    private lateinit var memoryStore: LocalMemoryStore
    private lateinit var toolRegistry: HairaToolRegistry
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localModel = LiteRtLocalModelEngine(cacheDir)
        memoryStore = LocalMemoryStore(this)
        toolRegistry = HairaToolRegistry(this)
        getSharedPreferences("haira_gateway", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("haira_secure", MODE_PRIVATE).edit().clear().apply()
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setSpeechRate(0.94f)
                tts?.setPitch(1.02f)
                if (tts?.setLanguage(Locale.getDefault()) == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
            }
        }
        setContent {
            HairaApp(
                context = this,
                modelEngine = localModel,
                memoryStore = memoryStore,
                initialModelPath = getSharedPreferences("haira_model", MODE_PRIVATE).getString("path", null),
                rememberModel = { path -> getSharedPreferences("haira_model", MODE_PRIVATE).edit().putString("path", path).apply() },
                speak = { text -> if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "haira_reply") },
                handleCommand = ::handleCommand,
                toolDescriptors = toolRegistry.tools
            )
        }
    }

    private fun handleCommand(raw: String): String? {
        toolRegistry.execute(raw)?.let { return it }
        val command = raw.lowercase(Locale.ROOT).trim()
        return try {
            when (command) {
                "open youtube", "youtube kholo", "youtube open karo" -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                    "YouTube open kar raha hoon."
                }
                "open settings", "settings kholo", "phone settings kholo" -> {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                    "Android Settings open kar raha hoon."
                }
                "open camera", "camera kholo", "camera open karo" -> {
                    startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
                    "Camera app open kar raha hoon. Camera image understanding abhi available nahi hai."
                }
                else -> if (command.startsWith("remind me to ") || command.startsWith("mujhe yaad dilana ")) {
                    val title = raw.substringAfter("to ", raw.substringAfter("dilana ", raw)).trim().ifBlank { "HAIRA reminder" }
                    startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, title)
                        .putExtra(CalendarContract.Events.DESCRIPTION, "Created from HAIRA AI"))
                    "Calendar reminder form open hua hai. Date aur time choose karke save karna."
                } else null
            }
        } catch (_: Exception) {
            "Ye action open nahi ho paya. Compatible Android app check karo."
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        if (::localModel.isInitialized) localModel.close()
        super.onDestroy()
    }
}

@Composable
private fun HairaApp(
    context: Context,
    modelEngine: LiteRtLocalModelEngine,
    memoryStore: LocalMemoryStore,
    initialModelPath: String?,
    rememberModel: (String) -> Unit,
    speak: (String) -> Unit,
    handleCommand: (String) -> String?,
    toolDescriptors: List<HairaToolDescriptor>
) {
    val messages = remember { mutableStateListOf<HairaMessage>().apply { addAll(memoryStore.loadMessages()) } }
    val memories = remember { mutableStateListOf<HairaMemory>().apply { addAll(memoryStore.loadMemories()) } }
    var tab by remember { mutableStateOf("HOME") }
    var seriousMode by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var memoryDraft by remember { mutableStateOf("") }
    var autoSpeak by remember { mutableStateOf(context.getSharedPreferences("haira_voice", Context.MODE_PRIVATE).getBoolean("auto_speak", false)) }
    var modelPath by remember { mutableStateOf(initialModelPath.orEmpty()) }
    var modelLoaded by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var modelStatus by remember { mutableStateOf(if (initialModelPath.isNullOrBlank()) "No local model imported yet." else "Checking saved model…") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SideEffect {
        (context as? Activity)?.requestedOrientation = if (seriousMode) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) draft = listOf(draft.trim(), spoken).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !modelBusy) scope.launch {
            modelBusy = true
            modelStatus = "Copying model into private app storage…"
            try {
                val file = copyModelIntoPrivateStorage(context, uri)
                modelStatus = "Loading model on CPU. First load may take several seconds…"
                withContext(Dispatchers.IO) { modelEngine.load(file) }
                modelPath = file.absolutePath
                rememberModel(file.absolutePath)
                modelLoaded = true
                modelStatus = "Local model ready: " + file.name
            } catch (error: Throwable) {
                modelLoaded = false
                modelStatus = "Model load failed: " + (error.message ?: "Unknown runtime error")
            } finally {
                modelBusy = false
            }
        }
    }

    LaunchedEffect(initialModelPath) {
        val saved = initialModelPath?.let { File(it) }
        if (saved != null && saved.isFile && saved.length() > 0L) {
            modelBusy = true
            modelStatus = "Loading saved model on CPU…"
            try {
                withContext(Dispatchers.IO) { modelEngine.load(saved) }
                modelLoaded = true
                modelPath = saved.absolutePath
                modelStatus = "Local model ready: " + saved.name
            } catch (error: Throwable) {
                modelStatus = "Saved model load failed: " + (error.message ?: "Unknown error")
            } finally {
                modelBusy = false
            }
        }
    }

    fun addMessage(text: String, fromUser: Boolean) {
        messages.add(HairaMessage(System.currentTimeMillis(), text, fromUser))
        while (messages.size > 500) messages.removeAt(0)
        memoryStore.saveMessages(messages.toList())
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        draft = ""
        addMessage(text, true)
        val localCommand = handleCommand(text)
        if (localCommand != null) {
            addMessage(localCommand, false)
            if (autoSpeak) speak(localCommand)
            return
        }
        if (!modelLoaded) {
            val reply = "Mera local AI model abhi load nahi hai. MODEL tab kholo aur compatible .litertlm file import karo. Chat history, memory vault, device TTS aur supported phone shortcuts local kaam karte hain; general AI chat ke liye model zaroori hai."
            addMessage(reply, false)
            if (autoSpeak) speak(reply)
            return
        }
        busy = true
        addMessage("Thinking on this device…", false)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { modelEngine.generate(text) } }
            busy = false
            if (messages.isNotEmpty() && !messages.last().fromUser && messages.last().text == "Thinking on this device…") messages.removeAt(messages.lastIndex)
            val answer = result.getOrElse { "Local inference failed: " + (it.message ?: "Unknown error") + ". Check model compatibility and free memory." }
            addMessage(answer, false)
            if (autoSpeak) speak(answer)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, background = Bg, surface = Panel)) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                if (!seriousMode) {
                    NavigationBar(containerColor = Color(0xFF09121E), contentColor = Cyan) {
                        listOf("HOME", "CHAT", "MEMORY", "MODEL", "GITHUB", "SETTINGS").forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Text(when (item) { "HOME" -> "⌂"; "CHAT" -> "◌"; "MEMORY" -> "▤"; "MODEL" -> "⬡"; "GITHUB" -> "⌘"; else -> "⚙" }, fontSize = 17.sp) },
                                label = { Text(item, fontSize = 8.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = Color(0xFF173044), unselectedIconColor = Muted, unselectedTextColor = Muted)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0C1D2B), Bg, Color(0xFF070B13))))
                    .padding(horizontal = 15.dp)
            ) {
                HairaHeader(modelLoaded) { seriousMode = true }
                if (seriousMode) {
                    WorkstationScreen(
                        context = context,
                        modelLoaded = modelLoaded,
                        toolDescriptors = toolDescriptors,
                        modelStatus = modelStatus,
                        modelBusy = modelBusy,
                        downloadProgress = downloadProgress,
                        onDownloadStarterModel = {
                            if (!modelBusy) scope.launch {
                                modelBusy = true
                                downloadProgress = 0f
                                modelStatus = "Preparing verified model download…"
                                try {
                                    val downloaded = HairaModelDownloadManager.download(context, HairaModelCatalog.starter) { progress ->
                                        scope.launch {
                                            downloadProgress = progress.fraction
                                            val percent = (progress.fraction * 100f).toInt()
                                            modelStatus = if (progress.total > 0L) "Downloading starter model: $percent%" else "Downloading starter model…"
                                        }
                                    }
                                    modelStatus = "Download verified. Loading local model on CPU…"
                                    withContext(Dispatchers.IO) { modelEngine.load(downloaded) }
                                    modelPath = downloaded.absolutePath
                                    rememberModel(downloaded.absolutePath)
                                    modelLoaded = true
                                    modelStatus = "Local model ready: " + downloaded.name
                                } catch (error: Throwable) {
                                    modelStatus = "Self-setup stopped: " + (error.message ?: "Unknown error")
                                } finally {
                                    modelBusy = false
                                }
                            }
                        },
                        onOpenModel = { seriousMode = false; tab = "MODEL" },
                        onReturnNormal = { seriousMode = false }
                    )
                } else when (tab) {
                    "HOME" -> HomeScreen(messages.size, memories.size, modelLoaded, modelStatus) { tab = "CHAT" }
                    "CHAT" -> ChatScreen(messages, draft, { draft = it }, { sendMessage() }, {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to HAIRA")
                        }
                        try { speechLauncher.launch(intent) } catch (_: Exception) { }
                    }, speak, busy)
                    "MEMORY" -> MemoryScreen(messages, memories, search, { search = it }, memoryDraft, { memoryDraft = it }, {
                        val note = memoryDraft.trim()
                        if (note.isNotEmpty()) {
                            memories.add(HairaMemory(System.currentTimeMillis(), note))
                            memoryStore.saveMemories(memories.toList())
                            memoryDraft = ""
                        }
                    }, { id ->
                        memories.removeAll { it.id == id }
                        memoryStore.saveMemories(memories.toList())
                    })
                    "MODEL" -> ModelScreen(modelPath, modelLoaded, modelBusy, modelStatus, { modelPicker.launch(arrayOf("*/*")) })
                    "GITHUB" -> GitHubManagerScreen()
                    else -> SettingsScreen(messages.size, memories.size, autoSpeak, { enabled ->
                        autoSpeak = enabled
                        context.getSharedPreferences("haira_voice", Context.MODE_PRIVATE).edit().putBoolean("auto_speak", enabled).apply()
                    }, {
                        messages.clear()
                        memoryStore.clearMessages()
                    }, {
                        messages.clear()
                        memories.clear()
                        memoryStore.clearAllLocalMemory()
                    })
                }
            }
        }
    }
}

private suspend fun copyModelIntoPrivateStorage(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val name = queryDisplayName(context, uri)
    require(name.endsWith(".litertlm", ignoreCase = true)) { "Choose a compatible .litertlm model file." }
    val folder = File(context.filesDir, "models").apply { mkdirs() }
    val temp = File(folder, "haira-model.importing")
    val target = File(folder, "haira-local-model.litertlm")
    context.contentResolver.openInputStream(uri)?.use { input ->
        temp.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
    } ?: error("Android could not open the selected model.")
    require(temp.length() > 0L) { "The selected model file is empty." }
    if (target.exists()) target.delete()
    check(temp.renameTo(target)) { "Could not finalize the model import." }
    target
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = "model.litertlm"
    val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = it.getString(index) ?: name
        }
    }
    return name
}

@Composable
private fun HairaHeader(modelLoaded: Boolean, onEnterSerious: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Color(0xFF092438), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("H", color = Cyan, fontSize = 24.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("HAIRA AI", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("LOCAL PERSONAL INTELLIGENCE", color = Cyan, fontSize = 7.sp, letterSpacing = 1.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (modelLoaded) "LOCAL AI READY" else "MODEL NOT LOADED", color = if (modelLoaded) Color(0xFF8BE0B0) else Gold, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text("BUILD 0.8.0", color = Muted, fontSize = 8.sp)
        }
        TextButton(onClick = onEnterSerious, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)) {
            Text("WORK MODE", color = Cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(messageCount: Int, memoryCount: Int, modelLoaded: Boolean, modelStatus: String, onChat: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Text("Good to see you, Sir ji.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Your private, on-device command space.", color = Muted, fontSize = 10.sp)
        }
        item { HoloCore(modelLoaded) }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("LOCAL AI CORE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.weight(1f))
                        Text(if (modelLoaded) "READY" else "SETUP REQUIRED", color = if (modelLoaded) Color(0xFF8BE0B0) else Gold, fontSize = 8.sp)
                    }
                    Text(modelStatus, color = Color(0xFFC0CFDA), fontSize = 10.sp, lineHeight = 15.sp)
                    OutlinedButton(onClick = onChat, modifier = Modifier.fillMaxWidth()) { Text("OPEN HAIRA CHAT  →", color = Cyan) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard("CHAT HISTORY", messageCount.toString(), "saved locally", Modifier.weight(1f))
                StatusCard("PINNED MEMORY", memoryCount.toString(), "saved notes", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard("VOICE OUTPUT", "DEVICE TTS", "phone voice engine", Modifier.weight(1f))
                StatusCard("AI CONNECTION", "OFFLINE", "no cloud AI API", Modifier.weight(1f))
            }
        }
        item { SettingRow("On-device inference", "LiteRT-LM runtime • CPU backend • model imported separately") }
        item { SettingRow("Privacy", "Chat history and pinned memories stay in app-private storage") }
        item { SettingRow("Voice input", "Android speech recognizer • offline availability depends on installed speech service") }
    }
}

@Composable
private fun HoloCore(active: Boolean) {
    val motion = androidx.compose.animation.core.rememberInfiniteTransition(label = "haira_core")
    val glow by motion.animateFloat(0.8f, 1f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800), repeatMode = androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    Box(Modifier.fillMaxWidth().height(175.dp).clip(RoundedCornerShape(22.dp)).background(Brush.radialGradient(listOf(Color(0xFF17445A), Color(0xFF0B1B2A), Color(0xFF07111D), Color.Transparent))), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFF0B2434), shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, Cyan.copy(alpha = 0.6f * glow)), modifier = Modifier.size(120.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("H", color = Cyan.copy(alpha = if (active) 1f else 0.65f), fontSize = 66.sp, fontWeight = FontWeight.Light) }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HAIRA CORE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text(if (active) "ON-DEVICE INFERENCE READY" else "LOCAL MODEL AWAITS IMPORT", color = Cyan, fontSize = 7.sp)
        }
    }
}

@Composable
private fun StatusCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(title, color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(value, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ChatScreen(messages: List<HairaMessage>, draft: String, onDraft: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit, speak: (String) -> Unit, busy: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Text("CONVERSATION", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Messages are stored locally on this device.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 6.dp))
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◉", color = Cyan, fontSize = 40.sp)
                    Text("Hello, Sir ji", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Import a local model to enable AI chat.", color = Muted, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(messages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (message.fromUser) Color(0xFF17465A) else Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 310.dp)) {
                            Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                                Text(if (message.fromUser) "YOU" else "HAIRA", color = if (message.fromUser) Color(0xFFB7E9F2) else Cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(message.text, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                                if (!message.fromUser && message.text.isNotBlank() && message.text != "Thinking on this device…") {
                                    TextButton(onClick = { speak(message.text) }, contentPadding = PaddingValues(0.dp)) { Text("▶ PLAY VOICE", color = Cyan, fontSize = 8.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 8.dp).clip(RoundedCornerShape(15.dp)).background(Panel).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp), cursorBrush = Brush.linearGradient(listOf(Cyan, Gold)), decorationBox = { inner -> Box { if (draft.isEmpty()) Text("Message HAIRA…", color = Muted, fontSize = 11.sp); inner() } })
            IconButton(onClick = onMic, enabled = !busy) { Text("🎙", fontSize = 16.sp) }
            Button(onClick = onSend, enabled = !busy && draft.isNotBlank(), contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)) { Text(if (busy) "…" else "SEND", fontSize = 9.sp) }
        }
    }
}

@Composable
private fun MemoryScreen(messages: List<HairaMessage>, memories: List<HairaMemory>, search: String, onSearch: (String) -> Unit, memoryDraft: String, onMemoryDraft: (String) -> Unit, onAddMemory: () -> Unit, onDeleteMemory: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("MEMORY VAULT", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Search chats and manage pinned notes saved on this phone.", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(bottom = 7.dp))
        OutlinedTextField(value = memoryDraft, onValueChange = onMemoryDraft, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Add a memory note…") }, singleLine = true)
        Button(onClick = onAddMemory, enabled = memoryDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("SAVE MEMORY NOTE") }
        OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search messages and notes…") }, singleLine = true)
        val saved = memories.filter { it.text.contains(search, true) }.asReversed()
        val history = messages.filter { it.text.contains(search, true) }.asReversed()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (saved.isNotEmpty()) item { Text("PINNED MEMORIES", color = Gold, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            items(saved, key = { "m" + it.id }) { item ->
                Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onDeleteMemory(item.id) }) { Text("DELETE", color = Color(0xFFFF9B9B), fontSize = 8.sp) }
                    }
                }
            }
            if (history.isNotEmpty()) item { Text("CHAT HISTORY", color = Cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            items(history, key = { "c" + it.id }) { item ->
                Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(9.dp)) {
                        Text(if (item.fromUser) "YOU" else "HAIRA", color = if (item.fromUser) Gold else Cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(item.text, color = Color.White, fontSize = 10.sp)
                        Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.time)), color = Muted, fontSize = 8.sp)
                    }
                }
            }
            if (saved.isEmpty() && history.isEmpty()) item { Text("No matching saved information.", color = Muted, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun ModelScreen(path: String, loaded: Boolean, busy: Boolean, status: String, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LOCAL MODEL MANAGER", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (loaded) "MODEL LOADED" else "NO MODEL ACTIVE", color = if (loaded) Color(0xFF8BE0B0) else Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(status, color = Color.White, fontSize = 10.sp, lineHeight = 15.sp)
                if (path.isNotBlank()) Text(File(path).name, color = Muted, fontSize = 8.sp)
                Button(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "PLEASE WAIT…" else "IMPORT .LITERTLM MODEL") }
            }
        }
        SettingRow("Runtime", "Google LiteRT-LM • CPU backend")
        SettingRow("Model format", "Compatible .litertlm file")
        SettingRow("Storage", "Model copied to HAIRA private storage; not bundled inside APK")
        SettingRow("AI network", "Inference runs locally. No AI API key or remote AI endpoint.")
        SettingRow("Device limits", "Model files may be large. Speed and stability depend on model size, free RAM, heat and storage. Start with a small model.")
        Text("A model file is required for general AI chat. Without one, chat history, pinned memories, device TTS and listed Android shortcuts remain available.", color = Muted, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun SettingsScreen(messageCount: Int, memoryCount: Int, autoSpeak: Boolean, onAutoSpeak: (Boolean) -> Unit, onClearChat: () -> Unit, onClearAll: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("SYSTEM SETTINGS", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speak replies automatically", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("Uses the phone's installed TTS voice.", color = Muted, fontSize = 8.sp)
                }
                Switch(checked = autoSpeak, onCheckedChange = onAutoSpeak)
            }
        }
        SettingRow("AI architecture", "Local LiteRT-LM inference • no remote AI endpoint")
        SettingRow("Conversation storage", messageCount.toString() + " messages saved locally")
        SettingRow("Pinned memories", memoryCount.toString() + " notes saved locally")
        SettingRow("Voice input", "Android speech recognizer • offline preference depends on installed speech service")
        SettingRow("Voice output", "Android TextToSpeech • installed device voices and languages")
        SettingRow("Microphone", "Speech recognizer requests microphone access when voice input starts")
        Surface(color = Color(0xFF29191B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("CLEAR LOCAL DATA", color = Color(0xFFFF9B9B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onClearChat, modifier = Modifier.fillMaxWidth()) { Text("CLEAR CHAT HISTORY", color = Color(0xFFFF9B9B), fontSize = 8.sp) }
                OutlinedButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) { Text("CLEAR ALL LOCAL MEMORY", color = Color(0xFFFF9B9B), fontSize = 8.sp) }
            }
        }
        Text("HAIRA AI • PART 1 • BUILD 0.6.0", color = Muted, fontSize = 8.sp)
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    Surface(color = Panel, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp)) {
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = Muted, fontSize = 8.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
