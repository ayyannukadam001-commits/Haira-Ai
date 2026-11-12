package com.haira.ai

import android.content.Context
import java.io.File

data class HairaToolDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val exampleCommand: String
)

/**
 * Registry for reviewed, app-bundled local tools.
 * It deliberately exposes a small workspace-only surface instead of executing downloaded code.
 */
class HairaToolRegistry(context: Context) {
    private val workspace = File(context.filesDir, "haira_workspace").apply { mkdirs() }

    val tools = listOf(
        HairaToolDescriptor("workspace.list", "Workspace File List", "Lists files in HAIRA's private workspace.", "list workspace files"),
        HairaToolDescriptor("workspace.note.create", "Create Note", "Creates a Markdown note inside the private workspace.", "create note: project ideas"),
        HairaToolDescriptor("workspace.search", "Workspace Search", "Searches text files inside the private workspace.", "search workspace: meeting"),
        HairaToolDescriptor("project.scaffold", "Project Scaffold", "Creates a safe starter project folder with a README and task plan.", "create project: My First App")
    )

    fun execute(rawCommand: String): String? {
        val command = rawCommand.trim()
        val normalized = command.lowercase()
        return when {
            normalized == "list workspace files" || normalized == "workspace files dikhao" -> listWorkspaceFiles()
            normalized.startsWith("create note:") -> createNote(command.substringAfter(':').trim())
            normalized.startsWith("search workspace:") -> searchWorkspace(command.substringAfter(':').trim())
            normalized.startsWith("create project:") -> createProject(command.substringAfter(':').trim())
            else -> null
        }
    }

    private fun listWorkspaceFiles(): String {
        val files = workspace.walkTopDown()
            .filter { it.isFile && it.parentFile != File(workspace, "tasks") }
            .take(100)
            .toList()
        if (files.isEmpty()) return "HAIRA workspace abhi empty hai."
        return "Workspace files (${files.size}):\n" + files.joinToString("\n") {
            "- ${it.relativeTo(workspace).path} (${it.length()} bytes)"
        }
    }

    private fun createNote(title: String): String {
        if (title.isBlank()) return "Note ka title missing hai. Example: create note: project ideas"
        val safeTitle = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(60).ifBlank { "HAIRA Note" }
        val base = safeTitle.lowercase().replace(Regex("\\s+"), "-").trim('-').ifBlank { "haira-note" }
        var file = File(workspace, "$base.md")
        var suffix = 2
        while (file.exists()) {
            file = File(workspace, "$base-$suffix.md")
            suffix++
        }
        file.writeText("# $safeTitle\n\nCreated by HAIRA AI.\n")
        return "Note create ho gayi: ${file.name}"
    }

    private fun createProject(title: String): String {
        if (title.isBlank()) return "Project name missing hai. Example: create project: My First App"
        val safeName = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(60).ifBlank { "New Project" }
        val folderName = safeName.lowercase().replace(Regex("\\s+"), "-").trim('-').ifBlank { "new-project" }
        val projectDir = File(workspace, folderName)
        if (projectDir.exists()) return "Project folder already exists: $folderName"
        if (!projectDir.mkdirs()) return "Project folder create nahi ho paya."
        File(projectDir, "README.md").writeText("# $safeName\n\nCreated by HAIRA AI local project scaffold.\n\n## Next steps\n- Define requirements\n- Implement features\n- Add tests\n")
        File(projectDir, "TASKS.md").writeText("# $safeName — Task Plan\n\n- [ ] Confirm requirements\n- [ ] Create project structure\n- [ ] Implement first feature\n- [ ] Test and review\n")
        return "Starter project create ho gaya: $folderName (README.md, TASKS.md). Ye scaffold hai, complete app nahi."
    }
    private fun searchWorkspace(query: String): String {
        if (query.isBlank()) return "Search word missing hai. Example: search workspace: meeting"
        val needle = query.take(100)
        val matches = workspace.walkTopDown()
            .filter { it.isFile && it.length() <= 256L * 1024L && it.extension.lowercase() in setOf("md", "txt", "kt", "kts", "json") }
            .take(100)
            .mapNotNull { file ->
                val line = runCatching { file.useLines { lines -> lines.firstOrNull { it.contains(needle, ignoreCase = true) } } }.getOrNull()
                if (line == null) null else "${file.relativeTo(workspace).path}: ${line.take(180)}"
            }
            .take(20)
            .toList()
        return if (matches.isEmpty()) "Workspace me '$needle' ka match nahi mila."
        else "Workspace search results:\n" + matches.joinToString("\n")
    }
}
