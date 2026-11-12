#!/usr/bin/env bash
set -euo pipefail

MODEL_REPO="mradermacher/NanoLM-0.3B-Instruct-v1.1-GGUF"
MODEL_FILE="NanoLM-0.3B-Instruct-v1.1.Q4_K_M.gguf"
MODEL_URL="https://huggingface.co/${MODEL_REPO}/resolve/main/${MODEL_FILE}"
MODEL_DIR="app/src/main/assets/models"
LICENSE_DIR="app/src/main/assets/licenses"
MODEL_PATH="${MODEL_DIR}/${MODEL_FILE}"

mkdir -p "$MODEL_DIR" "$LICENSE_DIR"

echo "Reading upstream model metadata and SHA-256..."
METADATA="$(curl --fail --silent --show-error --location --retry 4 "https://huggingface.co/api/models/${MODEL_REPO}/tree/main?recursive=false&expand=true")"
ENTRY="$(printf '%s' "$METADATA" | jq -c --arg file "$MODEL_FILE" '.[] | select(.path == $file)' | head -n 1)"
if [[ -z "$ENTRY" ]]; then
  echo "NanoLM model file metadata was not found upstream." >&2
  exit 1
fi

OID="$(printf '%s' "$ENTRY" | jq -r '.lfs.oid // .oid // empty')"
EXPECTED_SIZE="$(printf '%s' "$ENTRY" | jq -r '.lfs.size // .size // 0')"
SHA256="${OID#sha256:}"
if [[ ! "$SHA256" =~ ^[a-fA-F0-9]{64}$ ]]; then
  echo "Upstream did not provide a verifiable SHA-256 object id; refusing to bundle the model." >&2
  exit 1
fi
if (( EXPECTED_SIZE < 250000000 || EXPECTED_SIZE > 300000000 )); then
  echo "Unexpected NanoLM artifact size: ${EXPECTED_SIZE} bytes." >&2
  exit 1
fi

echo "Downloading NanoLM Q4_K_M (${EXPECTED_SIZE} bytes)..."
curl --fail --location --retry 5 --retry-all-errors --connect-timeout 30 --max-time 1800 "$MODEL_URL" -o "$MODEL_PATH"
ACTUAL_SIZE="$(stat -c '%s' "$MODEL_PATH")"
if [[ "$ACTUAL_SIZE" != "$EXPECTED_SIZE" ]]; then
  echo "Model size mismatch: expected ${EXPECTED_SIZE}, received ${ACTUAL_SIZE}." >&2
  exit 1
fi
printf '%s  %s\n' "$SHA256" "$MODEL_PATH" | sha256sum --check -

echo "Adding GPL-3.0 license and model attribution..."
curl --fail --silent --show-error --location --retry 3 https://www.gnu.org/licenses/gpl-3.0.txt -o "${LICENSE_DIR}/GPL-3.0.txt"
curl --fail --silent --show-error --location --retry 3 https://raw.githubusercontent.com/ffmpegkit-maintained/llama-android/main/LICENSE -o "${LICENSE_DIR}/LLAMA-ANDROID-MIT.txt"
curl --fail --silent --show-error --location --retry 3 https://raw.githubusercontent.com/ffmpegkit-maintained/llama-android/main/THIRD-PARTY-NOTICES.txt -o "${LICENSE_DIR}/LLAMA-ANDROID-THIRD-PARTY-NOTICES.txt"
cat > "${LICENSE_DIR}/NANOLM-NOTICE.txt" <<NOTICE
NanoLM-0.3B-Instruct-v1.1 Q4_K_M
Model source: https://huggingface.co/${MODEL_REPO}
Model file: ${MODEL_FILE}
Model license declared by upstream model card: GPL-3.0
Upstream SHA-256: ${SHA256}
Upstream file size: ${EXPECTED_SIZE} bytes

This model is bundled as a separate asset. The GGUF inference runtime is provided
by dev.ffmpegkit-maintained:llama-android (MIT; see its upstream project and
third-party notices). Review the model's upstream license and attribution before
redistributing modified builds.
NOTICE
