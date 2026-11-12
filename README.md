# HAIRA AI

## Part 5 — Bundled Local Model (v1.0.0)

Part 5 packages HAIRA's curated Qwen3 0.6B LiteRT-LM starter model into the APK build. The GitHub Actions workflow downloads the official model during the build and verifies its pinned SHA-256 before packaging it as an Android asset.

### First-launch setup
- No manual model-file picker is required for the bundled release APK.
- On first launch, HAIRA copies the packaged model into app-private storage.
- HAIRA verifies the model SHA-256 again before loading it with LiteRT-LM.
- The selected model path is saved locally and reused on later launches.
- The existing manual import and verified Wi-Fi download remain fallback paths for development builds or recovery.

### Bundled model
- Qwen3 0.6B LiteRT-LM
- Model file: `Qwen3-0.6B.litertlm`
- Approximate size: 586 MB
- License: Apache-2.0
- Source: `litert-community/Qwen3-0.6B`
- SHA-256: `c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108`

### Important limitations
- This APK bundles one lightweight language model, not five separate AI models.
- Chat and text generation use the bundled model. Image generation, video generation, advanced speech recognition, and 3D generation are not created merely by bundling this language model; those require separate compatible runtimes and models.
- The model is copied from the APK into app-private storage, so installation needs additional free space (keep at least about 1.2 GB free).
- The model runs locally; performance depends on available RAM, thermal state, Android version, and other running apps. First-launch model loading has not been verified on the user's physical phone.
- No cloud AI API key or remote AI inference is added.

### Previous parts retained
Part 1 core chat/runtime, Part 2 workstation and local project tools, Part 3 verified model setup and reviewed tool registry, and Part 4 user-started local task execution remain in the cumulative branch.

### Safety and honest limitations
- Only reviewed local tools can execute. No arbitrary shell commands, downloaded executable code, silent app installs, root access, or permission bypass.
- Task execution remains user-started and foreground-only.
- Heavy image/video/3D generation remains constrained by phone RAM, storage, thermal limits, and compatible local models.


## Work Mode tools and integration status

Work Mode retains reviewed local workspace tools, file management, the source/text editor and user-started task queue. The editor saves files; it does not compile or run arbitrary Android projects on-device.

Gmail and Google Drive browser shortcuts are not presented as working integrations in the redesign branch because they did not provide account-data access. Full account integrations would require a separate, reviewed OAuth implementation and explicit user consent.

Only reviewed app-bundled tool code is executable. This app does not install arbitrary plugin packages, silently run downloaded code, or bypass Android permissions.

## Redesign branch

The active redesign work is isolated on `feature/haira-redesign-hybrid-models-v1.1.0`. See [HAIRA_REDESIGN_PLAN.md](HAIRA_REDESIGN_PLAN.md) for the audit, removed placeholders, external-provider access checks, model compatibility gates and planned upgrades.

The previous v1.0.0 branch remains preserved. The redesign branch has not yet passed a new APK build or physical-device verification. App version remains 1.0.0 (versionCode 12) until that verification is complete.
