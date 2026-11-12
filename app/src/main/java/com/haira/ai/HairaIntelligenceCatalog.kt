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
            approximateSize = "~529 MB",
            format = "LiteRT-LM candidate",
            intendedRole = "Primary general assistant",
            runtime = "LiteRT-LM",
            status = "CANDIDATE — verification required",
            limitation = "Exact QAT artifact, pinned checksum, license and Redmi 13 5G performance must be verified before enabling download."
        ),
        HairaModelCandidate(
            id = "nanolm-03b-instruct",
            name = "NanoLM-0.3B-Instruct-v1.1",
            provider = "Community GGUF build",
            approximateSize = "~274 MB (Q4_K_M estimate)",
            format = "GGUF",
            intendedRole = "Secondary lightweight chat model",
            runtime = "Requires GGUF runtime; not compatible with current LiteRT engine",
            status = "CANDIDATE — runtime integration required",
            limitation = "Needs a separate GGUF-capable Android runtime, exact artifact/checksum and device tests."
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
