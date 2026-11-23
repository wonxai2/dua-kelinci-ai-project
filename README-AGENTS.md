# Agent Flow — Panduan Agents

Dokumen ini menjelaskan tiga agent Codex CLI yang dijalankan oleh dashboard Agent Flow. Setiap agent mempunyai role, sandbox, JSON schema, dan file output tersendiri, tetapi dapat meneruskan konteks ke agent berikutnya.

## Pipeline

```text
Input user
    │
    ▼
System Analyst
    │ output-agent-sa.json
    ▼
UI/UX Designer
    │ output-agent-uiux.json
    ▼
Fullstack Developer
    │ output-agent-fullstack.json
    ▼
Frontend + backend di workspace
```

Agent dapat dijalankan satu per satu. UI/UX Designer dan Fullstack Developer hanya dapat memakai mode berbasis output sebelumnya apabila file output upstream sudah tersedia.

## Daftar agent

### System Analyst

Script: `agents/system-analyst.sh`

Tugas:

- Menganalisis prompt dan kebutuhan bisnis.
- Menentukan kebutuhan fungsional dan nonfungsional.
- Menyusun rekomendasi arsitektur, data model, dan API.
- Menulis acceptance criteria dan risiko implementasi.

Konfigurasi:

- Sandbox: `read-only`
- Schema: `schemas/system-analyst.schema.json`
- Output: `output-agent-sa.json`
- Upstream: tidak ada

Agent ini boleh membaca file workspace, termasuk dataset seperti `data/sales.csv`, tetapi tidak boleh mengubah source code.

### UI/UX Designer

Script: `agents/uiux-designer.sh`

Tugas:

- Mengubah kebutuhan sistem menjadi user flow dan information architecture.
- Menentukan layar, komponen, state, dan perilaku responsif.
- Menyusun design system dan ketentuan accessibility.
- Menghasilkan `implementation_prompt` untuk developer.

Konfigurasi:

- Sandbox: `read-only`
- Schema: `schemas/uiux.schema.json`
- Output: `output-agent-uiux.json`
- Upstream: System Analyst

### Fullstack Developer

Script: `agents/fullstack-developer.sh`

Tugas:

- Membaca spesifikasi System Analyst dan UI/UX Designer.
- Mengimplementasikan frontend dan backend di workspace.
- Mempertahankan perubahan user yang sudah ada.
- Menjalankan pemeriksaan dan pengujian yang relevan.
- Menulis ringkasan implementasi dalam JSON.

Konfigurasi:

- Sandbox: `workspace-write`
- Schema: `schemas/fullstack.schema.json`
- Output: `output-agent-fullstack.json`
- Upstream: System Analyst dan UI/UX Designer

Agent ini dapat membuat atau mengubah file di workspace. Periksa output dan perubahan source code sebelum digunakan di production.

## Mode input prompt

Setiap card agent menyediakan tiga mode konteks.

### Hanya input prompt

Agent hanya menerima teks dari kolom **Input prompt**. Output agent sebelumnya tidak disertakan.

Gunakan mode ini untuk:

- Memulai System Analyst.
- Menjalankan agent secara independen.
- Mengganti spesifikasi upstream dengan instruksi baru.

### Prompt + output sebelumnya

Agent menerima input prompt dan seluruh output JSON dari agent upstream.

Gunakan mode ini untuk:

- Memberikan tambahan atau koreksi pada spesifikasi sebelumnya.
- Meneruskan pipeline sambil memberi arahan khusus.
- Menjelaskan batasan teknologi atau implementasi.

### Hanya output sebelumnya

Agent hanya menerima output JSON dari agent upstream. Isi kolom prompt tidak digunakan.

Gunakan mode ini untuk menjalankan pipeline secara murni berdasarkan hasil tahap sebelumnya.

Mode upstream dinonaktifkan untuk System Analyst karena agent tersebut merupakan tahap pertama.

## Menjalankan dashboard

Prasyarat:

- Bash
- Node.js 20 atau lebih baru
- Codex CLI sudah terpasang dan login

Jalankan secara lokal tanpa Docker:

```bash
./run-agent-dashboard.sh
```

Buka dashboard di:

```text
http://127.0.0.1:4173
```

Konfigurasi opsional:

```bash
PORT=8080 AGENT_IDLE_MS=300000 ./run-agent-dashboard.sh
```

`AGENT_IDLE_MS` menggunakan satuan milidetik. Nilai default `180000` berarti agent dihentikan jika tidak menghasilkan aktivitas stdout atau stderr selama tiga menit.

## Contoh workflow

Gunakan prompt berikut pada System Analyst:

```text
Buat aplikasi web kecil yang menampilkan penjualan per region dan brand,
menandai 5 outlet terlemah, dan menulis ringkasan satu paragraf yang bisa
dibaca sales manager di Telegram. Data diambil dari data/sales.csv.
```

Urutan pengerjaan:

1. Jalankan **System Analyst** dengan mode **Hanya input prompt**.
2. Pastikan `output-agent-sa.json` berhasil dibuat.
3. Jalankan **UI/UX Designer** dengan mode **Prompt + output sebelumnya** atau **Hanya output sebelumnya**.
4. Pastikan `output-agent-uiux.json` berhasil dibuat.
5. Jalankan **Fullstack Developer** dengan mode **Prompt + output sebelumnya** atau **Hanya output sebelumnya**.
6. Periksa source code, `output-agent-fullstack.json`, dan hasil verifikasi developer.

## Lifecycle agent

Saat tombol **Start agent** ditekan, server akan:

1. Memvalidasi mode input.
2. Membaca file output upstream jika diperlukan.
3. Menyusun prompt akhir di `runtime/<agent-id>.prompt.txt`.
4. Menjalankan shell script agent sebagai child process.
5. Merekam stdout dan stderr ke `runtime/<agent-id>.log`.
6. Mengirim perubahan status ke dashboard melalui Server-Sent Events.
7. Menyimpan jawaban Codex ke file history bertimestamp.
8. Jika run berhasil, memperbarui `latest.json` dan file kompatibilitas di root.

Tombol **Stop agent** mengirim `SIGTERM`. Jika proses masih berjalan setelah lima detik, server mengirim `SIGKILL`.

Status yang mungkin muncul:

- `idle` — belum dijalankan.
- `running` — Codex CLI sedang bekerja.
- `stopping` — perintah stop telah dikirim.
- `completed` — proses selesai dengan exit code `0`.
- `failed` — proses gagal.
- `stopped` — proses dihentikan oleh user atau idle timeout.

## File runtime dan output

```text
agents/
├── system-analyst.sh
├── uiux-designer.sh
└── fullstack-developer.sh

schemas/
├── system-analyst.schema.json
├── uiux.schema.json
└── fullstack.schema.json

runtime/
├── system-analyst.log
├── system-analyst.prompt.txt
├── uiux-designer.log
├── uiux-designer.prompt.txt
├── fullstack-developer.log
└── fullstack-developer.prompt.txt

outputs/
├── system-analyst/
│   ├── <timestamp>.json
│   └── latest.json
├── uiux-designer/
│   ├── <timestamp>.json
│   └── latest.json
└── fullstack-developer/
    ├── <timestamp>.json
    └── latest.json

output-agent-sa.json
output-agent-uiux.json
output-agent-fullstack.json
```

Isi `runtime/` bersifat sementara dan diabaikan oleh Git. Setiap run sukses menghasilkan file history baru di `outputs/<agent-id>/`. File `latest.json` diperbarui hanya setelah proses berhasil, sehingga run gagal tidak menghapus hasil valid sebelumnya. Output JSON di root tetap disinkronkan agar agent berikutnya dan integrasi lama tetap kompatibel.

## Environment variable untuk shell agent

Server mengirim environment variable berikut kepada setiap runner:

| Variable | Keterangan |
| --- | --- |
| `AGENT_ROOT` | Lokasi root workspace |
| `AGENT_PROMPT_FILE` | File prompt final yang sudah disusun server |
| `AGENT_OUTPUT_FILE` | Lokasi tujuan output JSON |

Ketiga variable wajib tersedia. Menjalankan script agent secara langsung tanpa variable tersebut akan menghasilkan error.

Contoh menjalankan System Analyst secara manual:

```bash
AGENT_ROOT="$PWD" \
AGENT_PROMPT_FILE="$PWD/runtime/system-analyst.prompt.txt" \
AGENT_OUTPUT_FILE="$PWD/output-agent-sa.json" \
./agents/system-analyst.sh
```

## Monitoring

- **Lihat log** menampilkan isi log dan memperbaruinya setiap 1,5 detik selama dialog terbuka.
- **Lihat output** menampilkan JSON terakhir yang dihasilkan agent.
- Status card diperbarui secara realtime melalui endpoint `/api/events`.
- PID proses ditampilkan ketika agent sedang berjalan.

## Troubleshooting

### `unexpected argument '-a' found`

Versi Codex CLI tertentu tidak menerima opsi `-a` pada `codex exec`. Runner saat ini tidak menggunakan opsi tersebut. Jika error masih muncul, pastikan dashboard menjalankan versi terbaru dari file di folder `agents/`.

### Output agent sebelumnya belum tersedia

Contoh:

```text
Output System Analyst belum tersedia: output-agent-sa.json
```

Jalankan agent upstream terlebih dahulu atau ubah mode menjadi **Hanya input prompt**.

### Codex CLI tidak ditemukan

Pastikan perintah berikut berhasil:

```bash
codex --version
```

Jika tidak, pasang Codex CLI dan pastikan executable tersedia di `PATH` shell yang menjalankan dashboard.

### Agent dihentikan setelah tiga menit

Agent dihentikan apabila tidak ada output stdout atau stderr dalam periode idle. Naikkan batas waktu apabila pekerjaan Codex membutuhkan proses panjang tanpa output:

```bash
AGENT_IDLE_MS=600000 ./run-agent-dashboard.sh
```

### Port sudah digunakan

Gunakan port lain:

```bash
PORT=8080 ./run-agent-dashboard.sh
```

### JSON tidak terbentuk

Periksa **Lihat log** pada card terkait. Penyebab umum:

- Codex CLI belum login.
- Schema tidak kompatibel dengan versi Codex CLI.
- Proses dihentikan oleh idle timeout.
- Prompt terlalu besar setelah menggabungkan output upstream.
- Workspace atau file dataset tidak dapat dibaca.

## Catatan keamanan

- Jangan menaruh credential, token Telegram, atau secret lain di kolom prompt.
- Jangan commit isi `runtime/` karena dapat memuat prompt dan log eksekusi.
- System Analyst dan UI/UX Designer menggunakan sandbox `read-only`.
- Fullstack Developer memiliki izin menulis ke workspace; selalu review perubahan yang dibuat.
- Runner tidak memakai `--dangerously-bypass-approvals-and-sandbox`.
