# HAIRA redesign and hybrid-model plan

## Branch safety
- Redesign work is isolated on feature/haira-redesign-hybrid-models-v1.1.0.
- The successful v1.0.0 branch is preserved unchanged.
- App version remains 1.0.0 / versionCode 12 until the redesign passes a build and device checks.
- No APK build has been triggered from this redesign branch yet.

## Changes already made on this branch
1. Normal-mode navigation is reduced from six crowded destinations to four: Home, Chat, Memory and More. Model Center, GitHub Manager, External Model Access and Settings are reached through More.
2. Removed the duplicate Research and Studios placeholder panels from Work Mode. The useful Files, Code editor, Tools, Self Setup and Task Queue panels remain.
3. Removed unused Gmail/Drive website-shortcut callback wiring. These shortcuts did not provide actual Gmail/Drive data integration, so they should not be presented as working plugins.
4. Removed startup code that cleared the haira_gateway and haira_secure preferences on every launch. Saved values should not be silently erased during app startup.
5. Added an External Model Access screen. It probes public Hugging Face and OpenRouter model-directory endpoints and distinguishes public directory access from authenticated inference. Groq and Gemini are shown as key-required providers.

## Model integration audit — do not misrepresent readiness

### Current working baseline
- The existing app uses LiteRT-LM only.
- The existing v1.0.0 APK workflow bundles Qwen3 0.6B LiteRT-LM (about 586 MB). Keep it as a fallback until replacement models are proven on-device.
- The current LiteRT engine cannot load arbitrary GGUF files.

### Candidate A — Gemma 3 1B INT4 QAT
- The dynamic-int4 QAT benchmark entry is about 529 MB and links to a MediaPipe .task artifact; the repository lists the corresponding .task file at about 555 MB.
- The LiteRT-LM-compatible .litertlm variant is about 584 MB, not 529 MB. The current HAIRA engine loads .litertlm, so using the 529 MB QAT artifact would require a MediaPipe runtime path instead.
- The Hugging Face repository is gated and requires a logged-in user to accept the Gemma license before downloading.
- Before adding automatic in-app download, choose the exact runtime/artifact pair, verify its checksum, and decide how the license acceptance is handled without asking the user to paste a private token into HAIRA.
- Samsung S24 Ultra benchmark figures are not Redmi 13 5G benchmarks.

### Candidate B — NanoLM-0.3B-Instruct-v1.1 Q4_K_M
- The GGUF quantized file is about 274 MB.
- Intended runtime: llama.cpp / a compatible GGUF runtime, not the existing LiteRT-LM engine.
- The model card says it supports English and Chinese and performs better on English tasks; Hinglish quality must be tested rather than assumed.
- The published GGUF repository identifies GPL-3.0. Preserve the model card and license notices and review redistribution implications before packaging or mirroring weights.
- The model page currently says it is not deployed by an Inference Provider, so it cannot be treated as a ready-to-use hosted external model.

### Runtime compatibility gate
- Do not show Gemma or NanoLM as installed/working until their exact files load and generate a test response.
- NanoLM needs a separate GGUF runtime. A candidate Kotlin binding, tensai-app/llama.kt, currently requires minSdk 29, JDK 21, NDK 27 and arm64-v8a; it is an early-stage project without a published Maven artifact. Evaluate and pin a tested revision before integrating it.
- Do not remove Qwen3 until both replacement candidates pass load, generation, memory, thermal and recovery tests on the target phone.

## External model discovery rules
- A public model directory being reachable without a key does not mean hosted inference is keyless.
- Hugging Face Inference Providers require an access token for inference.
- OpenRouter's public model catalogue can be browsed without a key, but its completion API requires an API key.
- Gemini API and Groq inference require provider authentication.
- HAIRA must not bypass authentication, scrape private sessions, invent credentials, or send private chat history to providers without explicit user consent.
- The discovery screen currently checks public catalogue reachability only; it does not claim to have tested inference for every model.

## Planned upgrades after runtime verification
1. Introduce a common model-engine interface with LiteRT-LM and GGUF/llama.cpp implementations.
2. Add a model manager with per-model format, size, license, source, checksum, download progress, resume, storage preflight and delete controls.
3. Keep large model weights outside the APK; use verified first-launch or user-selected downloads where licensing/access permits.
4. Add a task router that chooses only installed local models and explicitly enabled, authenticated external providers.
5. Keep external AI optional; if offline or unavailable, fall back to the local model.
6. Improve generation responsiveness with streaming, session reuse and bounded chat context, then measure on the target phone.
7. Preserve user control over memory, voice, notifications and external data sharing.

## Features retained
- Local chat and chat history
- Searchable/pinned memory
- Device TTS and Android speech-recognition handoff (offline support depends on installed services)
- Work Mode local tools, file workspace, code/text editor and task queue
- GitHub Manager
- Safe Android app shortcuts

## Features removed or de-emphasized
- Duplicate Research and Studios placeholder panels
- Gmail/Drive browser shortcuts presented as if they were integrations
- Any claim that the app can execute arbitrary downloaded plugins or automatically run unreviewed code

## Verification status
- Source edits committed to the redesign branch.
- No build or physical-device verification yet.