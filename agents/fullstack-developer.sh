#!/usr/bin/env bash
set -euo pipefail
ROOT="${AGENT_ROOT:?AGENT_ROOT wajib diisi}"
PROMPT_FILE="${AGENT_PROMPT_FILE:?AGENT_PROMPT_FILE wajib diisi}"
OUTPUT_FILE="${AGENT_OUTPUT_FILE:?AGENT_OUTPUT_FILE wajib diisi}"

ROLE='Anda adalah Fullstack Developer senior. Implementasikan sistem backend dan frontend lengkap di workspace berdasarkan konteks. Inspeksi kode yang ada, pertahankan perubahan user, buat implementasi production-minded, dan jalankan verifikasi yang relevan. Jangan sekadar memberi contoh: kerjakan file proyek. Jawaban akhir wajib berupa ringkasan mengikuti JSON schema.'
{
  printf '%s\n\nSpesifikasi dan instruksi:\n' "$ROLE"
  cat "$PROMPT_FILE"
} | codex exec - --ephemeral --skip-git-repo-check -C "$ROOT" -s workspace-write --color never --output-schema "$ROOT/schemas/fullstack.schema.json" -o "$OUTPUT_FILE"
