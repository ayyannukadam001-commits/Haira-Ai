package com.haira.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HairaModelSpec(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val maxBytes: Long,
    val sha256: String,
    val license: String,
    val sourceLabel: String
)

data class HairaDownloadProgress(val downloaded: Long, val total: Long) {
    val fraction: Float get() = if (total <= 0L) 0f else (downloaded.toFloat() / total).coerceIn(0f, 1f)
}

/** Curated catalog: each downloadable model must have a known HTTPS host and SHA-256. */
object HairaModelCatalog {
    // SHA-256 matches the 584,417,280-byte LiteRT-LM artifact documented by the model publisher.
    // The mirror hosts the same bytes; Gemma terms still apply and are confirmed in the app before download.
    val gemma3 = HairaModelSpec(
        id = "gemma3-1b-int4-qat",
        name = "Gemma 3 1B INT4 QAT",
        description = "Primary local assistant model. 584 MB LiteRT-LM artifact.",
        fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
        downloadUrl = "https://huggingface.co/cuesetac/gemma-3-1b-it-litertlm/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
        maxBytes = 700L * 1024L * 1024L,
        sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        license = "Gemma Terms of Use",
        sourceLabel = "Hugging Face mirror of LiteRT Community artifact"
    )

    val starter = HairaModelSpec(
        id = "qwen3-0.6b-litertlm",
        name = "Qwen3 0.6B (LiteRT-LM)",
        description = "586 MB bundled starter text model. CPU speed and compatibility vary by phone.",
        fileName = "Qwen3-0.6B.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        maxBytes = 900L * 1024L * 1024L,
        sha256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
        license = "Apache-2.0",
        sourceLabel = "LiteRT Community on Hugging Face"
    )
}

object HairaModelDownloadManager {
    private const val BUFFER_SIZE = 256 * 1024
    private const val MIN_FREE_SPACE = 1200L * 1024L * 1024L

    suspend fun download(
        context: Context,
        spec: HairaModelSpec,
        onProgress: (HairaDownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(spec.downloadUrl.startsWith("https://huggingface.co/")) { "Model source is not on the approved HTTPS host." }
        require(spec.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Model catalog entry has no valid SHA-256 checksum." }

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        require(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            "Internet connection is unavailable."
        }
        require(!connectivity.isActiveNetworkMetered) {
            "Large model downloads use unmetered Wi-Fi only. Connect to Wi-Fi and retry."
        }

        val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
        val stat = StatFs(modelDirectory.absolutePath)
        require(stat.availableBytes >= MIN_FREE_SPACE) {
            "Not enough free storage. Keep at least 1.2 GB free before downloading this model."
        }

        val partial = File(modelDirectory, spec.fileName + ".part")
        val target = File(modelDirectory, spec.fileName)
        var offset = if (partial.exists()) partial.length() else 0L
        if (offset > spec.maxBytes) {
            partial.delete()
            offset = 0L
        }

        var completed = false
        var lastFailure: Throwable? = null
        for (attempt in 1..5) {
            var connection: HttpURLConnection? = null
            try {
                val currentOffset = if (partial.exists()) partial.length() else 0L
                connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 45000
                    readTimeout = 120000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "HAIRA-AI-Android/1.1")
                    setRequestProperty("Accept-Encoding", "identity")
                    setRequestProperty("Connection", "keep-alive")
                    if (currentOffset > 0L) setRequestProperty("Range", "bytes=$currentOffset-")
                }
                connection.connect()
                val code = connection.responseCode
                require(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                    "Model server returned HTTP $code. Retry later or check the model host."
                }
                val append = currentOffset > 0L && code == HttpURLConnection.HTTP_PARTIAL
                if (!append) partial.delete()
                val responseLength = connection.contentLengthLong
                val total = if (append && responseLength > 0L) currentOffset + responseLength else responseLength
                connection.inputStream.use { input ->
                    FileOutputStream(partial, append).buffered(BUFFER_SIZE).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = if (append) currentOffset else 0L
                        var lastReport = downloaded
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            require(downloaded <= spec.maxBytes) { "Download exceeded the catalog size limit and was stopped." }
                            if (downloaded - lastReport >= 1024 * 1024 || (total > 0L && downloaded == total)) {
                                onProgress(HairaDownloadProgress(downloaded, total))
                                lastReport = downloaded
                            }
                        }
                        output.flush()
                    }
                }
                if (total > 0L) require(partial.length() >= total) {
                    "Download connection ended early. Retrying from the saved partial download…"
                }
                completed = true
                lastFailure = null
                break
            } catch (error: Throwable) {
                lastFailure = error
                if (error is IllegalArgumentException || error is SecurityException) throw error
                if (attempt == 5) break
                kotlinx.coroutines.delay(1500L * attempt)
            } finally {
                connection?.disconnect()
            }
        }
        if (!completed) throw java.io.IOException(
            "Model download failed after 5 attempts. Partial data is saved; reopen HAIRA on stable Wi-Fi to resume. Cause: " +
                (lastFailure?.message ?: "connection interrupted"),
            lastFailure
        )

        require(partial.length() > 0L) { "The model download was empty." }
        val actualHash = sha256(partial)
        require(actualHash.equals(spec.sha256, ignoreCase = true)) {
            partial.delete()
            "SHA-256 verification failed. The downloaded file was rejected."
        }
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Verified model could not be moved into the model folder." }
        target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
