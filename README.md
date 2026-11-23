# Agent Flow — Codex CLI Orchestrator

## Dashboard Penjualan

Implementasi aplikasi yang dihasilkan tersedia sebagai dua modul terpisah:

- `backend/`: Java 21 + Spring Boot, membaca `data/sales.csv` secara read-only.
- `frontend/`: React + Vite, dashboard responsif berbahasa Indonesia.

Jalankan tanpa Docker pada dua terminal:

```bash
./run-local.sh backend
./run-local.sh frontend
```

Buka <http://127.0.0.1:5173>. Backend tersedia di <http://127.0.0.1:8080>. Untuk verifikasi, jalankan `cd backend && mvn test` dan `cd frontend && npm run build`.

Konfigurasi opsional: `SALES_CSV_PATH=/path/file.csv ./run-local.sh backend` dan `SERVER_PORT=8081 ./run-local.sh backend`.

## Dashboard Agent Lama

Dashboard lokal untuk menjalankan tiga agent Codex CLI yang saling terintegrasi:

- **System Analyst** → `output-agent-sa.json`
- **UI/UX Designer** → `output-agent-uiux.json`
- **Fullstack Developer** → `output-agent-fullstack.json` dan implementasi source code

## Menjalankan

Prasyarat: Node.js 20+, Bash, Codex CLI yang sudah login.

Cara paling sederhana, tanpa Docker dan tanpa instalasi dependency npm:

```bash
chmod +x run-agent-dashboard.sh
./run-agent-dashboard.sh
```

Atau jalankan langsung melalui npm:

```bash
npm start
```

Buka <http://127.0.0.1:4173>. Tidak ada dependency npm yang perlu di-install.

Port dan idle timeout dapat diubah saat menjalankan launcher:

```bash
PORT=8080 AGENT_IDLE_MS=300000 ./run-agent-dashboard.sh
```

## Mode konteks

Setiap card menyediakan tiga pilihan:

1. **Hanya input prompt** — agent hanya menerima teks pada card.
2. **Prompt + output sebelumnya** — menggabungkan input dengan JSON agent upstream.
3. **Hanya output sebelumnya** — menjalankan agent murni dari output upstream.

Alur upstream: System Analyst → UI/UX Designer → Fullstack Developer. Mode upstream dinonaktifkan untuk System Analyst karena tidak memiliki pendahulu.

## Runtime dan keamanan

- SA dan UI/UX berjalan dengan sandbox `read-only`.
- Fullstack berjalan dengan sandbox `workspace-write`. Eksekusi noninteraktif mengikuti kebijakan approval bawaan `codex exec` pada versi CLI yang terpasang.
- Proses tanpa output stdout/stderr selama 3 menit dihentikan otomatis. Ubah dengan `AGENT_IDLE_MS=300000 npm start`.
- Log sementara disimpan di `runtime/`; output JSON berada di root proyek.
- Tombol **Stop agent** mengirim `SIGTERM`, lalu `SIGKILL` setelah 5 detik jika proses belum selesai.
