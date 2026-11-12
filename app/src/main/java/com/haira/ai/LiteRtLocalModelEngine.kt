package com.haira.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * On-device text generation. Model weights stay in app-private storage and never leave the device.
 * A model must be imported by the user before general AI chat can run.
 */
class LiteRtLocalModelEngine(private val cacheDirectory: File) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "haira-local-llm").apply { isDaemon = true }
    }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelPath: String? = null

    val isLoaded: Boolean get() = conversation != null
    val currentModelPath: String? get() = loadedModelPath

    suspend fun load(modelFile: File) = withContext(dispatcher) {
        require(modelFile.isFile && modelFile.length() > 0L) { "Selected model file is missing or empty." }
        closeInternal()
        val candidate = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDirectory.absolutePath
            )
        )
        try {
            candidate.initialize()
            val newConversation = candidate.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are HAIRA AI, a helpful on-device personal assistant. " +
                            "Work offline, be honest about uncertainty, and never claim an action happened unless it did. " +
                            "Use concise, clear answers. The user prefers Roman Hinglish when they write in Hinglish."
                    ),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = 0.7)
                )
            )
            engine = candidate
            conversation = newConversation
            loadedModelPath = modelFile.absolutePath
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    suspend fun generate(prompt: String): String = withContext(dispatcher) {
        val active = conversation ?: error("No local model is loaded. Import a compatible .litertlm model first.")
        val answer = StringBuilder()
        active.sendMessageAsync(prompt).collect { message ->
            answer.append(message.toString())
        }
        answer.toString().trim().ifBlank { "The local model returned an empty response. Try again with a shorter message." }
    }

    fun close() {
        executor.execute {
            closeInternal()
            dispatcher.close()
        }
    }

    private fun closeInternal() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
        loadedModelPath = null
    }
}
