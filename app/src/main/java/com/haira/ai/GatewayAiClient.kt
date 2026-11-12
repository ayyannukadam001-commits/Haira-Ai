package com.haira.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class GatewayConfig(
    val provider: String = "FreeLLMAPI",
    val baseUrl: String = "http://127.0.0.1:3001/v1",
    val model: String = "auto",
    val apiKey: String = ""
)

/** OpenAI-compatible client for FreeLLMAPI, OmniRoute, and compatible custom gateways. */
class GatewayAiClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun testConnection(config: GatewayConfig, callback: (Result<List<String>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val connection = open(config, "/models", "GET")
                try {
                    val code = connection.responseCode
                    val body = readBody(connection, code)
                    if (code !in 200..299) throw IllegalStateException(httpError(code, body))
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    buildList {
                        for (i in 0 until data.length()) {
                            val id = data.optJSONObject(i)?.optString("id").orEmpty()
                            if (id.isNotBlank()) add(id)
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            main.post { callback(result) }
        }
    }

    fun send(
        config: GatewayConfig,
        history: List<Pair<String, String>>,
        callback: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                require(config.model.isNotBlank()) { "Model ID is empty. Set a model in Settings." }
                val connection = open(config, "/chat/completions", "POST")
                try {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    val messages = JSONArray()
                    history.takeLast(24).forEach { (role, text) ->
                        if (text.isNotBlank()) messages.put(JSONObject().put("role", role).put("content", text))
                    }
                    val payload = JSONObject()
                        .put("model", config.model.trim())
                        .put("messages", messages)
                        .put("stream", false)
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val body = readBody(connection, code)
                    if (code !in 200..299) throw IllegalStateException(httpError(code, body))
                    val root = JSONObject(body)
                    val choices = root.optJSONArray("choices")
                        ?: throw IllegalStateException("Gateway returned no choices. Check the selected model and gateway logs.")
                    val message = choices.optJSONObject(0)?.optJSONObject("message")
                        ?: throw IllegalStateException("Gateway response format was not recognized.")
                    val content = message.opt("content")
                    val reply = when (content) {
                        is String -> content
                        is JSONArray -> buildString {
                            for (i in 0 until content.length()) {
                                val part = content.optJSONObject(i)
                                if (part != null) append(part.optString("text"))
                            }
                        }
                        else -> ""
                    }.trim()
                    if (reply.isBlank()) throw IllegalStateException("Gateway returned an empty reply.")
                    reply
                } finally {
                    connection.disconnect()
                }
            }
            main.post { callback(result) }
        }
    }

    private fun open(config: GatewayConfig, path: String, method: String): HttpURLConnection {
        val base = config.baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Server URL must start with http:// or https://."
        }
        val parsedUrl = URL(base)
        if (parsedUrl.protocol == "http" && !isPrivateOrLoopbackHost(parsedUrl.host)) {
            throw IllegalArgumentException("Use HTTPS for remote gateway servers. HTTP is allowed only for localhost or a private LAN address.")
        }
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 90_000
            setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            }
        }
        return connection
    }

    private fun isPrivateOrLoopbackHost(rawHost: String): Boolean {
        val host = rawHost.removePrefix("[").removeSuffix("]").lowercase()
        if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun httpError(code: Int, body: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return when (code) {
            401, 403 -> "Authentication failed (HTTP $code). Check the gateway key."
            404 -> "Endpoint not found (HTTP 404). Base URL should usually end in /v1."
            429 -> "Gateway rate limit or provider quota reached (HTTP 429)."
            502, 503, 504 -> "Gateway is running but its provider could not answer (HTTP $code)."
            else -> "Gateway request failed (HTTP $code).${detail?.let { " $it" } ?: ""}"
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}
