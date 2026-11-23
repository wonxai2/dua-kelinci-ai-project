#!/usr/bin/env bash
set -euo pipefail
ROOT="${AGENT_ROOT:?AGENT_ROOT wajib diisi}"
PROMPT_FILE="${AGENT_PROMPT_FILE:?AGENT_PROMPT_FILE wajib diisi}"
OUTPUT_FILE="${AGENT_OUTPUT_FILE:?AGENT_OUTPUT_FILE wajib diisi}"

ROLE='Anda adalah System Analyst senior. Analisis kebutuhan secara tajam, hilangkan ambiguitas dengan asumsi yang dinyatakan, dan hasilkan spesifikasi yang siap dipakai designer serta developer. Jangan mengubah file proyek. Jawaban akhir wajib mengikuti JSON schema.'
{
  printf '%s\n\nPermintaan dan konteks:\n' "$ROLE"
  cat "$PROMPT_FILE"
} | codex exec - --ephemeral --skip-git-repo-check -C "$ROOT" -s read-only --color never --output-schema "$ROOT/schemas/system-analyst.schema.json" -o "$OUTPUT_FILE"
