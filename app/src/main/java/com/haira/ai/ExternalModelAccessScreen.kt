package com.haira.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExternalProvider(
    val id: String,
    val name: String,
    val catalogUrl: String?,
    val catalogAccess: String,
    val inferenceAccess: String,
    val note: String
)

private data class ProviderProbe(val status: String, val detail: String)

private object ExternalProviderCatalog {
    val providers = listOf(
        ExternalProvider(
            "huggingface",
            "Hugging Face",
            "https://huggingface.co/api/models?pipeline_tag=text-generation&limit=1",
            "Public model directory: no key",
            "Hosted inference: access token required",
            "A public model page does not mean that hosted inference is free or anonymous."
        ),
        ExternalProvider(
            "openrouter",
            "OpenRouter",
            "https://openrouter.ai/api/v1/models",
            "Public model directory: no key",
            "Inference: API key required",
            "Some listed models may have free pricing, but requests still require an account key."
        ),
        ExternalProvider(
            "groq",
            "Groq",
            null,
            "No unauthenticated directory probe",
            "API key required",
            "HAIRA will not request, store or invent a provider key."
        ),
        ExternalProvider(
            "gemini",
            "Google Gemini API",
            null,
            "No unauthenticated directory probe",
            "API key required",
            "This is optional external access and is separate from HAIRA's local models."
        )
    )

    suspend fun probe(provider: ExternalProvider): ProviderProbe = withContext(Dispatchers.IO) {
        val endpoint = provider.catalogUrl
            ?: return@withContext ProviderProbe("KEY REQUIRED", "Official inference access requires authentication; no keyless probe is available.")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "HAIRA-AI-Android")
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code in 200..299) {
                ProviderProbe("DIRECTORY ONLINE", "Public model directory responded with HTTP $code. Inference still requires authentication.")
            } else {
                ProviderProbe("DIRECTORY UNAVAILABLE", "Directory returned HTTP $code. Inference access requirement remains: " + provider.inferenceAccess + ".")
            }
        } catch (error: Exception) {
            ProviderProbe("CHECK FAILED", error.message?.take(120) ?: "Network unavailable.")
        } finally {
            connection?.disconnect()
        }
    }
}

@Composable
fun ExternalModelAccessScreen() {
    var results by remember { mutableStateOf<Map<String, ProviderProbe>>(emptyMap()) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runCheck() {
        if (checking) return
        checking = true
        scope.launch {
            val next = LinkedHashMap<String, ProviderProbe>()
            ExternalProviderCatalog.providers.forEach { provider ->
                next[provider.id] = ExternalProviderCatalog.probe(provider)
                results = next.toMap()
            }
            checking = false
        }
    }

    LaunchedEffect(Unit) { runCheck() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("EXTERNAL MODEL ACCESS", color = Color(0xFF55E6F2), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(
            "HAIRA checks public model directories and shows whether hosted inference requires authentication. Directory access and AI generation are different things.",
            color = Color(0xFF8CA4B8), fontSize = 9.sp, lineHeight = 14.sp
        )
        Button(onClick = { runCheck() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (checking) "CHECKING PROVIDERS…" else "CHECK AGAIN")
        }
        Surface(color = Color(0xFF251B14), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("IMPORTANT", color = Color(0xFFFFD18A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    "A directory responding without a key does not mean its AI models can generate answers without a key. HAIRA currently has no verified anonymous hosted-inference provider.",
                    color = Color(0xFFC8B9A8), fontSize = 9.sp, lineHeight = 14.sp
                )
            }
        }
        ExternalProviderCatalog.providers.forEach { provider ->
            val result = results[provider.id]
            Surface(color = Color(0xFF0D1826), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(provider.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(result?.status ?: if (checking) "CHECKING" else "NOT CHECKED", color = Color(0xFF55E6F2), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(provider.catalogAccess, color = Color(0xFF8CA4B8), fontSize = 9.sp)
                    Text(provider.inferenceAccess, color = Color(0xFFFFD18A), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Text(result?.detail ?: provider.note, color = Color(0xFF8CA4B8), fontSize = 8.sp, lineHeight = 12.sp)
                }
            }
        }
        Text(
            "Checks are limited to public catalog endpoints. HAIRA does not attempt anonymous inference, bypass sign-in or send private chat data to these services.",
            color = Color(0xFF8CA4B8), fontSize = 8.sp, lineHeight = 12.sp
        )
    }
}
