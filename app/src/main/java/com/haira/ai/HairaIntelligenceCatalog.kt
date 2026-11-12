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
    val localCandidates = listOf(
        HairaModelCandidate(
            id = "gemma3-1b-int4-qat",
            name = "Gemma 3 1B INT4 QAT",
            provider = "Google",
            approximateSize = "~584 MB download (529 MB benchmark model size)",
            format = "LiteRT-LM (.litertlm)",
            intendedRole = "Primary general assistant",
            runtime = "LiteRT-LM",
            status = "CANDIDATE — Gemma terms acceptance required",
            limitation = "The public repository is gated: a Hugging Face account must accept the Gemma terms. The 584 MB .litertlm artifact has no pinned checksum in this app yet; Redmi 13 5G testing is required."
        ),
        HairaModelCandidate(
            id = "nanolm-03b-instruct",
            name = "NanoLM-0.3B-Instruct-v1.1",
            provider = "Community GGUF build",
            approximateSize = "274 MB (Q4_K_M)",
            format = "GGUF",
            intendedRole = "Secondary lightweight chat model",
            runtime = "Requires GGUF runtime; not compatible with current LiteRT engine",
            status = "CANDIDATE — GPL-3.0 + runtime review required",
            limitation = "Model card declares GPL-3.0. Needs a separate GGUF-capable Android runtime, exact artifact/checksum, distribution-license review and device tests."
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
    // Only providers whose official documentation explicitly describes the
    // authentication method are listed. Public model pages do not imply free inference.
    val providers = listOf(
        HairaExternalProvider(
            id = "huggingface-inference",
            name = "Hugging Face Inference Providers",
            access = "Hugging Face user token required",
            notes = "Free credits may be available, but inference requests still require a token. This is not a no-key service."
        )
    )

    val noKeyVerifiedProviders: List<HairaExternalProvider> = emptyList()
}
