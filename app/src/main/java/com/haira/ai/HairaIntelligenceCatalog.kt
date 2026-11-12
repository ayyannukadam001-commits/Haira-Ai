package com.haira.ai

/**
 * HAIRA model/provider inventory.
 *
 * Entries marked CANDIDATE are not downloadable until their exact artifact,
 * checksum, license, tokenizer/template and Android runtime have been verified.
 */
data class HairaModelCandidate(
    val id: String,
    val name: String,
    val provider: String,
    val approximateSize: String,
    val format: String,
    val intendedRole: String,
    val runtime: String,
    val status: String,
    val limitation: String
)

object HairaIntelligenceCatalog {
    const val gemmaOfficialModelPage = "https://huggingface.co/litert-community/Gemma3-1B-IT"
    const val nanoLmGgufPage = "https://huggingface.co/mradermacher/NanoLM-0.3B-Instruct-v1.1-GGUF"

    val localCandidates = listOf(
        HairaModelCandidate(
            id = "gemma3-1b-int4-qat",
            name = "Gemma 3 1B INT4 QAT",
            provider = "Google",
            approximateSize = "~584 MB (584,417,280 bytes)",
            format = "LiteRT-LM (.litertlm)",
            intendedRole = "Primary general assistant",
            runtime = "LiteRT-LM",
            status = "IN-APP DOWNLOAD AVAILABLE — TERMS ACKNOWLEDGEMENT REQUIRED",
            limitation = "HAIRA downloads a 584,417,280-byte LiteRT-LM artifact from a Hugging Face mirror and checks its pinned SHA-256 before loading. The in-app checkbox records the user's acknowledgement; it does not verify a Google account or official gated access. Review the Gemma Terms and model provenance. Redmi 13 5G performance testing remains pending."
        ),
        HairaModelCandidate(
            id = "nanolm-03b-instruct",
            name = "NanoLM-0.3B-Instruct-v1.1",
            provider = "Community GGUF build",
            approximateSize = "274 MB (Q4_K_M)",
            format = "GGUF",
            intendedRole = "Secondary lightweight chat model",
            runtime = "llama.cpp Android CPU runtime",
            status = "BUNDLED IN APK — DEVICE PERFORMANCE TEST PENDING",
            limitation = "Q4_K_M GGUF is fetched during the GitHub Actions build, verified against upstream SHA-256 metadata, and packaged in the APK. It is extracted into app-private storage on first launch. GPL-3.0 license and attribution are included. Roman Hinglish quality and Redmi 13 5G speed still require device testing."
        ),
        HairaModelCandidate(
            id = "qwen3-06b-litert",
            name = "Qwen3 0.6B",
            provider = "LiteRT Community",
            approximateSize = "~586 MB",
            format = "LiteRT-LM",
            intendedRole = "Existing recovery model",
            runtime = "LiteRT-LM 0.18.1",
            status = "CURRENT BASELINE / KEEP AS BACKUP",
            limitation = "Retain until replacement models pass real-device verification."
        )
    )

    // Do not label any provider as no-key until official docs and a safe
    // documented availability check both confirm that access method.
    val externalAccessStates = listOf(
        "No-key access documented and verified",
        "API key required",
        "Account / OAuth login required",
        "Unavailable / rate limited",
        "Unknown — not yet verified"
    )
}

data class HairaExternalProvider(
    val id: String,
    val name: String,
    val access: String,
    val notes: String
)

object HairaExternalProviderCatalog {
    // No hosted inference-provider integrations are included in this build.
    // External provider discovery stays disabled until a provider is explicitly
    // approved and its official access method can be verified without guessing.
    val providers: List<HairaExternalProvider> = emptyList()

    val noKeyVerifiedProviders: List<HairaExternalProvider> = emptyList()
}
