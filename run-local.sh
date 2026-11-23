#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "${1:-}" in
  backend)
    cd "$ROOT_DIR/backend"
    SALES_CSV_PATH="${SALES_CSV_PATH:-$ROOT_DIR/data/sales.csv}" exec mvn spring-boot:run
    ;;
  frontend)
    cd "$ROOT_DIR/frontend"
    if [[ ! -d node_modules ]]; then npm install; fi
    exec npm run dev
    ;;
  *)
    echo "Penggunaan: ./run-local.sh backend|frontend" >&2
    exit 2
    ;;
esac
