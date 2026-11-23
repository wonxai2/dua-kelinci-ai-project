#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-4173}"
AGENT_IDLE_MS="${AGENT_IDLE_MS:-180000}"

usage() {
  cat <<'EOF'
Penggunaan: ./run-agent-dashboard.sh

Menjalankan Agent Flow secara lokal tanpa Docker.

Environment variable:
  PORT             Port HTTP dashboard (default: 4173)
  AGENT_IDLE_MS    Batas idle agent dalam milidetik (default: 180000)

Contoh:
  ./run-agent-dashboard.sh
  PORT=8080 AGENT_IDLE_MS=300000 ./run-agent-dashboard.sh
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
  "") ;;
  *) printf 'Error: argumen tidak dikenal: %s\n\n' "$1" >&2; usage >&2; exit 2 ;;
esac

if ! command -v node >/dev/null 2>&1; then
  printf 'Error: Node.js belum terpasang. Gunakan Node.js 20 atau lebih baru.\n' >&2
  exit 1
fi

NODE_MAJOR="$(node -p 'Number(process.versions.node.split(".")[0])')"
if (( NODE_MAJOR < 20 )); then
  printf 'Error: Node.js 20+ dibutuhkan. Versi aktif: %s\n' "$(node --version)" >&2
  exit 1
fi

if ! command -v codex >/dev/null 2>&1; then
  printf 'Error: Codex CLI tidak ditemukan di PATH.\n' >&2
  exit 1
fi

if ! [[ "$PORT" =~ ^[0-9]+$ ]] || (( PORT < 1 || PORT > 65535 )); then
  printf 'Error: PORT harus berupa angka antara 1–65535.\n' >&2
  exit 1
fi

if ! [[ "$AGENT_IDLE_MS" =~ ^[0-9]+$ ]] || (( AGENT_IDLE_MS < 1000 )); then
  printf 'Error: AGENT_IDLE_MS harus berupa angka minimal 1000.\n' >&2
  exit 1
fi

chmod +x "$SCRIPT_DIR"/agents/*.sh
mkdir -p "$SCRIPT_DIR/runtime"

printf '\nAgent Flow — Codex CLI Orchestrator\n'
printf 'Dashboard : http://127.0.0.1:%s\n' "$PORT"
printf 'Idle stop : %s ms\n' "$AGENT_IDLE_MS"
printf 'Workspace : %s\n' "$SCRIPT_DIR"
printf 'Hentikan  : tekan Ctrl+C\n\n'

cd "$SCRIPT_DIR"
export PORT AGENT_IDLE_MS
exec node server.mjs
