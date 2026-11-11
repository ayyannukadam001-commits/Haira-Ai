package com.haira.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Owns LiteRT-LM on one background thread; model work never blocks the UI. */
class LocalAiEngine(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun load(modelPath: String, onReady: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                if (conversation != null) {
                    mainHandler.post { onReady() }
                    return@launch
                }
                val candidate = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        cacheDir = appContext.cacheDir.absolutePath
                    )
                )
                candidate.initialize()
                engine = candidate
                conversation = candidate.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "You are HAIRA, a helpful personal AI assistant. Address the user respectfully as Sir ji. " +
                                "Be clear, useful and honest about uncertainty. Never claim a phone action succeeded unless confirmed."
                        ),
                        // Initial turns also avoid empty-preface issues in the Qwen3 model template.
                        initialMessages = listOf(
                            Message.user("Introduce yourself briefly as HAIRA."),
                            Message.model("Hello, Sir ji. I am HAIRA, your local AI assistant.")
                        )
                    )
                )
                mainHandler.post { onReady() }
            } catch (error: Exception) {
                closeEngineOnly()
                mainHandler.post { onError(error.message ?: "Model could not be loaded.") }
            }
        }
    }

    fun send(
        prompt: String,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val activeConversation = conversation
                    ?: throw IllegalStateException("Load the local AI model first.")
                activeConversation.sendMessageAsync(prompt).collect { message ->
                    val chunk = message.toString()
                    mainHandler.post { onChunk(chunk) }
                }
                mainHandler.post { onDone() }
            } catch (error: Exception) {
                mainHandler.post { onError(error.message ?: "AI response failed.") }
            }
        }
    }

    private fun closeEngineOnly() {
        try { conversation?.close() } catch (_: Exception) { }
        conversation = null
        try { engine?.close() } catch (_: Exception) { }
        engine = null
    }

    fun close() {
        scope.launch {
            closeEngineOnly()
            dispatcher.close()
            executor.shutdown()
        }
    }
}
