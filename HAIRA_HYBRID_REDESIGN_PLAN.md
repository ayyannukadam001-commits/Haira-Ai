# HAIRA v1.1.0 — Hybrid Redesign Work Plan

## Working branch
`feature/haira-hybrid-redesign-v1.1.0`

Base: Part 5 / v1.0.0 commit `51737385c451a654ef6bc2ca4db65683ba2e66ff`.

## Product direction
Keep HAIRA local-first and useful offline. External services are optional enhancements, never mandatory for core chat. No paid service may be used without explicit user approval. Never bypass provider authentication, quotas, or access controls.

## Model architecture

### Local models requested
1. Gemma 3 1B INT4 QAT — primary candidate, pending exact artifact/runtime/device verification.
2. NanoLM-0.3B-Instruct-v1.1 — secondary candidate, requested quantization Q4_K_M (~274 MB).

Important compatibility constraint:
- Current app engine uses LiteRT-LM and accepts `.litertlm` model files.
- NanoLM's selected ~274 MB artifact is GGUF, not `.litertlm`. It cannot be passed to the existing LiteRT-LM engine.
- NanoLM therefore requires a separate GGUF-capable runtime (for example, a reviewed Android llama.cpp integration), model-specific tokenizer/chat-template handling, native ABI packaging, and device tests. Do not add it to the existing LiteRT catalog as if it were compatible.
- Keep current Qwen3 model available as recovery/backup until both new models pass real device verification.
- Do not bundle all model weights inside the APK. Use user-visible, resumable, checksum-verified downloads and explicit storage/network controls.

### Model routing
- Add a local model registry with model format/runtime metadata.
- Route general chat to the selected local model; allow the user to choose primary/secondary.
- Keep model loading lazy and release inactive engines to control RAM/heat.
- Never load both models simultaneously by default on a mid-range phone.
- Report actual active model and measured first-token/decode speed; do not promise benchmark numbers from other phones.

## External Model Discovery
- Add a curated provider/model directory, not unrestricted endpoint probing.
- For each entry show: provider, task, official access method, key required / login required / no-key documented, free quota (if officially stated), last checked, and status.
- Check provider documentation and perform only documented, low-impact health/availability checks.
- Never attempt credential bypass, anonymous scraping, quota circumvention, or hidden use of paid endpoints.
- No-key access must be marked only when the provider officially documents it and the check succeeds.
- If no free no-key provider is available, clearly say so and continue with local models/tools.
- External AI must be opt-in per provider; no user data is sent without clear notice and consent.

## Redesign principles
- Simplify navigation around Chat, Workspace, Models, Tools, Memory, and Settings.
- Keep existing working chat, local memory, voice input/output, model manager, and useful workspace tools.
- Remove or hide nonfunctional placeholder panels and website shortcuts that imply full integrations.
- Rename Gmail/Drive website shortcuts as browser links or remove them until real OAuth integrations exist.
- No fake buttons or claims of image/video/3D generation unless complete pipelines are implemented and tested.
- Preserve portrait chat usability, keyboard-safe composer, readable typography, and low-power animations.
- Keep Workstation as a focused workspace mode rather than a collection of placeholder studios.

## Feature tracking
- Existing chat/history/pinned memory: retain and regression-test.
- Existing Android speech recognition and device TTS: retain; label offline capability as device-dependent.
- Workspace notes/search/project scaffold: retain if tests pass.
- Gmail/Drive shortcuts: relabel honestly or remove from plugin list.
- Qwen3 bundled model: retain as temporary backup until replacement verification.
- Gemma 3 1B: integrate only after verified artifact, checksum, license and runtime test.
- NanoLM GGUF: integrate only after adding and testing a compatible runtime.
- External Model Discovery: implement with documented-provider metadata and safe checks.
- Unsupported image/video/3D generation placeholders: remove; do not imply capability.

## Acceptance gates
1. Build succeeds on GitHub Actions.
2. App launches without crash on Android 16 / Redmi 13 5G class device.
3. Portrait chat composer remains above keyboard with no overlap.
4. Existing chat, history, memory, voice, and workspace functions regress cleanly.
5. Each local model loads only with its matching runtime and verified artifact.
6. Model switching releases previous engine and handles low-memory failures.
7. External discovery distinguishes key, login, no-key, unavailable, and unknown; unknown is never mislabeled no-key.
8. No paid inference or external data transfer occurs without explicit user consent.
9. APK size and first-run download sizes are reported honestly.

## Current verified baseline observations
- v1.0.0 app uses LiteRT-LM 0.18.1 and one LiteRT model engine.
- Current bundled model is Qwen3 0.6B.
- Current workspace tools are local: file list, notes, workspace search, project scaffold.
- Gmail and Drive currently open official websites only; they do not provide account-data access.
- Current speech input uses Android speech recognition intent; actual offline support depends on installed speech service.
- Current speech output uses Android TextToSpeech.
- Existing model import UI allows manual `.litertlm` import.
