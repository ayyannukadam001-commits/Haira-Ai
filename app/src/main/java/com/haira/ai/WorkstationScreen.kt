package com.haira.ai

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WorkBg = Color(0xFF050912)
private val WorkPanel = Color(0xFF0D1826)
private val WorkCyan = Color(0xFF55E6F2)
private val WorkMuted = Color(0xFF8CA4B8)
private val WorkGold = Color(0xFFD7B56D)

private data class WorkTask(val id: String, val title: String, val state: String)

@Composable
fun WorkstationScreen(context: Context, modelLoaded: Boolean, toolDescriptors: List<HairaToolDescriptor>, modelStatus: String, modelBusy: Boolean, downloadProgress: Float, onDownloadStarterModel: () -> Unit, onOpenModel: () -> Unit, onReturnNormal: () -> Unit, onExecuteTask: (String) -> String?) {
    val workspace = remember { File(context.filesDir, "haira_workspace").apply { mkdirs() } }
    var section by remember { mutableStateOf("OVERVIEW") }
    var files by remember { mutableStateOf(emptyList<File>()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var editorText by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("untitled.kt") }
    var newTask by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf(emptyList<WorkTask>()) }
    var executingTaskId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Private local workspace ready.") }
    val taskScope = rememberCoroutineScope()

    fun refresh() {
        files = workspace.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        tasks = File(workspace, "tasks").listFiles()?.filter { it.extension == "task" }?.mapNotNull { f ->
            val lines = runCatching { f.readLines() }.getOrNull() ?: return@mapNotNull null
            val title = lines.firstOrNull().orEmpty()
            if (title.isBlank()) null else {
                val storedState = lines.getOrNull(1) ?: "QUEUED"
                val safeState = if (storedState == "RUNNING") "INTERRUPTED" else storedState
                if (safeState != storedState) runCatching { f.writeText(title + "\n" + safeState) }
                WorkTask(f.name, title, safeState)
            }
        } ?: emptyList()
    }
    LaunchedEffect(Unit) { refresh() }

    Row(Modifier.fillMaxSize().background(WorkBg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("HAIRA", color = WorkCyan, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("SERIOUS MODE", color = WorkGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = WorkCyan.copy(alpha = 0.25f))
            listOf("OVERVIEW", "SELF SETUP", "TOOLS", "FILES", "CODE", "RESEARCH", "TASK QUEUE", "STUDIOS").forEach { item ->
                TextButton(onClick = { section = item }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(5.dp)) {
                    Text((if (section == item) "▸ " else "") + item, color = if (section == item) WorkCyan else WorkMuted, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onReturnNormal, modifier = Modifier.fillMaxWidth()) { Text("NORMAL MODE", fontSize = 9.sp) }
            Text("PRIVATE LOCAL WORKSPACE", color = WorkMuted, fontSize = 7.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(section, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("HAIRA AI • LOCAL-FIRST WORKSTATION • v1.0.0", color = WorkMuted, fontSize = 8.sp)
                }
                Text(if (modelLoaded) "MODEL READY" else "MODEL NOT LOADED", color = if (modelLoaded) Color(0xFF8BE0B0) else WorkGold, fontSize = 8.sp)
            }
            when (section) {
                "OVERVIEW" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Metric("FILES", files.size.toString(), Modifier.weight(1f))
                        Metric("OPEN TASKS", tasks.count { it.state != "DONE" }.toString(), Modifier.weight(1f))
                        Metric("DONE", tasks.count { it.state == "DONE" }.toString(), Modifier.weight(1f))
                    }
                    Panel("PROJECT WORKSPACE", "Create and edit real local project files. Files remain in HAIRA's private app storage.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Button(onClick = { section = "FILES" }) { Text("FILE MANAGER", fontSize = 9.sp) }
                            OutlinedButton(onClick = { section = "CODE" }) { Text("CODE STUDIO", fontSize = 9.sp) }
                        }
                    }
                    Panel("LOCAL AI ENGINE", modelStatus) { OutlinedButton(onClick = onOpenModel) { Text("MODEL MANAGER", fontSize = 9.sp) } }
                    Panel("TASK TRACKER", "Tracks queued and completed work. It does not claim background execution or fabricated progress.") { OutlinedButton(onClick = { section = "TASK QUEUE" }) { Text("OPEN QUEUE", fontSize = 9.sp) } }
                }
                "SELF SETUP" -> {
                    Panel("AUTOMATIC MODEL SETUP", "HAIRA includes its starter LiteRT-LM model inside this APK. On first launch it copies the model into private storage, verifies SHA-256 and loads it automatically.") {
                        Text("STARTER MODEL", color = WorkCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(HairaModelCatalog.starter.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(HairaModelCatalog.starter.description, color = WorkMuted, fontSize = 9.sp)
                        Text("Source: ${HairaModelCatalog.starter.sourceLabel} • License: ${HairaModelCatalog.starter.license}", color = WorkMuted, fontSize = 8.sp)
                        Text("The bundled model is installed automatically on first launch. Keep about 1.2 GB free storage for the private copy. Manual download remains available as a fallback.", color = WorkGold, fontSize = 8.sp)
                        if (modelBusy) {
                            LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth())
                            Text("Setup in progress…", color = WorkCyan, fontSize = 9.sp)
                        }
                        Button(onClick = onDownloadStarterModel, enabled = !modelBusy && !modelLoaded, modifier = Modifier.fillMaxWidth()) {
                            Text(if (modelLoaded) "LOCAL MODEL READY" else if (modelBusy) "SETTING UP…" else "DOWNLOAD & SET UP MODEL", fontSize = 9.sp)
                        }
                    }
                    Panel("INTEGRATION SAFETY", "Only catalogued model files with a pinned SHA-256 checksum are accepted. HAIRA does not silently install arbitrary executable apps or code. Tool adapters are integrated as reviewed app modules.")
                    Panel("MODEL STATUS", modelStatus) {
                        OutlinedButton(onClick = onOpenModel, enabled = !modelBusy) { Text("OPEN MODEL MANAGER", fontSize = 9.sp) }
                    }
                }
                "TOOLS" -> {
                    Text("REGISTERED LOCAL TOOLS", color = WorkCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("These reviewed tool adapters are bundled with HAIRA and operate only on their declared local scope.", color = WorkMuted, fontSize = 9.sp)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(toolDescriptors, key = { it.id }) { tool ->
                            Panel(tool.name, tool.description) {
                                Text("ID: ${tool.id}", color = WorkMuted, fontSize = 8.sp)
                                Text("Try in chat: ${tool.exampleCommand}", color = WorkCyan, fontSize = 8.sp)
                            }
                        }
                    }
                    Panel("SECURITY BOUNDARY", "HAIRA does not download or execute arbitrary code as a tool. New tool adapters must be reviewed and integrated as app modules; model/data downloads use a separate checksum-verified path.")
                }
                "FILES" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Workspace files", color = WorkMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            var name = "new-note.md"; var n = 1
                            while (File(workspace, name).exists()) { name = "new-note-" + n + ".md"; n++ }
                            val f = File(workspace, name); f.writeText("# HAIRA Note\n\n")
                            selectedFile = f; fileName = f.name; editorText = f.readText(); refresh(); status = "Created " + f.name
                        }) { Text("NEW FILE", fontSize = 9.sp) }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(files, key = { it.absolutePath }) { f ->
                            Surface(color = if (selectedFile == f) Color(0xFF153246) else WorkPanel, shape = RoundedCornerShape(8.dp), onClick = {
                                selectedFile = f; fileName = f.name; editorText = runCatching { f.readText() }.getOrDefault(""); status = "Opened " + f.name
                            }) {
                                Row(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(f.name, color = Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                    Text(f.length().toString() + " bytes", color = WorkMuted, fontSize = 8.sp)
                                }
                            }
                        }
                        if (files.isEmpty()) item { Text("No files yet. Create one with NEW FILE.", color = WorkMuted, fontSize = 10.sp) }
                    }
                    Text(status, color = WorkMuted, fontSize = 8.sp)
                }
                "CODE" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = fileName, onValueChange = { fileName = it.filter { c -> c.isLetterOrDigit() || c in "._-" } }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("File name") })
                        Spacer(Modifier.width(7.dp))
                        Button(onClick = {
                            val name = fileName.trim()
                            if (name.isNotBlank() && !name.contains("..")) {
                                val f = File(workspace, name); f.writeText(editorText); selectedFile = f; refresh(); status = "Saved " + f.name
                            } else status = "Enter a valid file name."
                        }) { Text("SAVE", fontSize = 9.sp) }
                    }
                    Surface(color = Color(0xFF07111D), shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BasicTextField(value = editorText, onValueChange = { editorText = it }, modifier = Modifier.fillMaxSize().padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFD6E6EF), fontSize = 11.sp, lineHeight = 16.sp))
                    }
                    Text("Editor saves source/text files. It does not compile or run Android projects on-device.", color = WorkMuted, fontSize = 8.sp)
                    Text(status, color = WorkMuted, fontSize = 8.sp)
                }
                "RESEARCH" -> {
                    Text("Research notebook", color = WorkCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Create a local Markdown note for questions, findings and source links.", color = WorkMuted, fontSize = 9.sp)
                    Button(onClick = { section = "CODE"; fileName = "research-notes.md"; editorText = "# Research Notes\n\n## Question\n\n## Findings\n\n## Sources\n" }) { Text("CREATE RESEARCH NOTE", fontSize = 9.sp) }
                    Text("This notebook does not claim to browse the internet; research sources must be gathered separately.", color = WorkMuted, fontSize = 8.sp)
                }
                "TASK QUEUE" -> {
                    Text("Add a supported local command. Examples: create note: project ideas • create project: My App • list workspace files • search workspace: meeting", color = WorkMuted, fontSize = 9.sp)
                    OutlinedTextField(value = newTask, onValueChange = { newTask = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Task command") }, singleLine = true)
                    Button(onClick = {
                        val title = newTask.trim()
                        if (title.isNotEmpty()) {
                            val dir = File(workspace, "tasks").apply { mkdirs() }
                            File(dir, "task-" + System.currentTimeMillis() + ".task").writeText(title + "\nQUEUED")
                            newTask = ""; refresh(); status = "Task queued. Press RUN when ready."
                        }
                    }, enabled = newTask.isNotBlank()) { Text("ADD TO QUEUE", fontSize = 9.sp) }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(tasks, key = { it.id }) { task ->
                            Surface(color = WorkPanel, shape = RoundedCornerShape(8.dp)) {
                                Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, color = Color.White, fontSize = 10.sp)
                                        Text(task.state, color = if (task.state == "DONE") Color(0xFF8BE0B0) else if (task.state == "FAILED" || task.state == "INTERRUPTED") Color(0xFFFF8A80) else WorkGold, fontSize = 8.sp)
                                    }
                                    if (task.state != "DONE" && task.state != "RUNNING") TextButton(enabled = executingTaskId == null, onClick = {
                                        val dir = File(workspace, "tasks")
                                        val f = File(dir, task.id).takeIf { it.isFile }
                                        if (f != null) {
                                            tasks = tasks.map { if (it.id == task.id) it.copy(state = "RUNNING") else it }
                                            taskScope.launch {
                                                executingTaskId = task.id
                                                try {
                                                    val execution = withContext(Dispatchers.IO) {
                                                        runCatching {
                                                            f.writeText(task.title + "\nRUNNING")
                                                            onExecuteTask(task.title)
                                                        }
                                                    }
                                                    withContext(Dispatchers.IO) {
                                                        f.writeText(task.title + "\n" + if (execution.getOrNull() == null) "FAILED" else "DONE")
                                                    }
                                                    status = execution.fold(
                                                        onSuccess = { it ?: "Unsupported command. Use one of the listed local tool commands." },
                                                        onFailure = { "Task failed: " + (it.message ?: "Unknown error") }
                                                    )
                                                    refresh()
                                                } finally {
                                                    executingTaskId = null
                                                }
                                            }
                                        }
                                    }) { Text("RUN", color = WorkCyan, fontSize = 8.sp) }
                                }
                            }
                        }
                        if (tasks.isEmpty()) item { Text("Queue is empty.", color = WorkMuted, fontSize = 10.sp) }
                    }
                    Text(status, color = WorkMuted, fontSize = 8.sp)
                    Text("Execution is user-started and foreground-only. Only reviewed local workspace tools run; no shell commands, downloaded code, or silent background execution.", color = WorkGold, fontSize = 8.sp)
                }
                "STUDIOS" -> {
                    Panel("CODING STUDIO", "Available now: create, open and save text/source files.") { OutlinedButton(onClick = { section = "CODE" }) { Text("OPEN EDITOR", fontSize = 9.sp) } }
                    Panel("PROJECT STUDIO", "Available now: local project workspace and file manager.") { OutlinedButton(onClick = { section = "FILES" }) { Text("OPEN FILE MANAGER", fontSize = 9.sp) } }
                    Panel("RESEARCH STUDIO", "Available now: local Markdown research notebook.") { OutlinedButton(onClick = { section = "RESEARCH" }) { Text("OPEN NOTEBOOK", fontSize = 9.sp) } }
                    Panel("DESIGN / IMAGE / VIDEO / 3D", "Generation is not active yet. These need compatible local models and rendering pipelines; no fake generate controls are shown.")
                    Panel("VOICE STUDIO", "Part 1 device speech input and TextToSpeech remain in Normal Mode. Advanced voice configuration is deferred.")
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = WorkPanel, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(Modifier.padding(9.dp)) {
            Text(label, color = WorkMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text(value, color = WorkCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Panel(title: String, description: String, action: @Composable (() -> Unit)? = null) {
    Surface(color = WorkPanel, shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = WorkCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(description, color = Color(0xFFC0CFDA), fontSize = 9.sp, lineHeight = 14.sp)
            action?.invoke()
        }
    }
}
