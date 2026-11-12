# HAIRA AI

## Part 3 — Self-Setup & Execution Foundation (v0.8.0)

This branch adds a curated local model setup flow:
- Downloads a LiteRT-LM starter model from a pinned HTTPS source.
- Uses unmetered Wi-Fi only and checks available storage before downloading.
- Resumes an interrupted partial download when the server supports byte ranges.
- Verifies the complete model with a pinned SHA-256 checksum before activating it.
- Stores the model in app-private storage and loads it through the existing LiteRT-LM CPU engine.

The initial starter model is Qwen3 0.6B LiteRT-LM (approximately 614 MB, Apache-2.0). Device performance and successful loading are not guaranteed; actual behavior must be tested on the target phone.

Safety and scope:
- Model downloads start only after the user taps the setup button.
- Arbitrary executable apps or code are not silently installed.
- Only curated model entries with pinned checksums are accepted.
- This is the first Part 3 foundation, not a claim that all autonomous tools or task execution are complete.
