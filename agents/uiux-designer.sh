#!/usr/bin/env bash
set -euo pipefail
ROOT="${AGENT_ROOT:?AGENT_ROOT wajib diisi}"
PROMPT_FILE="${AGENT_PROMPT_FILE:?AGENT_PROMPT_FILE wajib diisi}"
OUTPUT_FILE="${AGENT_OUTPUT_FILE:?AGENT_OUTPUT_FILE wajib diisi}"

ROLE='Anda adalah UI/UX Designer senior. Ubah kebutuhan menjadi UX yang jelas, aksesibel, responsif, dan design direction yang implementable. Hasilkan implementation_prompt yang bisa langsung digunakan developer. Jangan mengubah file proyek. Jawaban akhir wajib mengikuti JSON schema.'
{
  printf '%s\n\nPermintaan dan konteks:\n' "$ROLE"
  cat "$PROMPT_FILE"
} | codex exec - --ephemeral --skip-git-repo-check -C "$ROOT" -s read-only --color never --output-schema "$ROOT/schemas/uiux.schema.json" -o "$OUTPUT_FILE"
