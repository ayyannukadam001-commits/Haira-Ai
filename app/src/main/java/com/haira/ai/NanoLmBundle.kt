package com.haira.ai

import android.content.Context
import java.io.File

/**
 * Copies the build-time bundled NanoLM asset into app-private storage.
 * The APK asset is never modified; the copied GGUF is the file opened by llama.cpp.
 */
fun copyBundledNanoLm(context: Context): File {
    val fileName = "NanoLM-0.3B-Instruct-v1.1.Q4_K_M.gguf"
    val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
    val target = File(modelDirectory, fileName)
    if (target.isFile && target.length() in 250_000_000L..300_000_000L) return target

    val temporary = File(modelDirectory, "$fileName.extracting")
    if (temporary.exists()) temporary.delete()
    context.assets.open("models/$fileName").buffered().use { input ->
        temporary.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
    }
    require(temporary.length() in 250_000_000L..300_000_000L) {
        "Bundled NanoLM asset is missing or has an unexpected size."
    }
    if (target.exists()) target.delete()
    check(temporary.renameTo(target)) { "NanoLM could not be extracted into app storage." }
    return target
}
