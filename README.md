# SimonJP Document Generator

REST API untuk membuat dokumen **PDF, Word (.docx), Excel (.xlsx), dan PowerPoint (.pptx)** dari satu model
JSON. Semua format mendukung teks kaya (font, ukuran, tebal, miring, garis bawah, warna), tabel, gambar, dan
grafik.

Dokumen Word/Excel/PowerPoint memakai **grafik native** yang tetap bisa diedit di Office/LibreOffice.
PDF dibuat dengan **Apache PDFBox** dan digambar langsung sebagai vektor — tidak lewat HTML, tidak mengonversi
file Office, dan grafiknya bukan gambar raster.

## Fitur

| Fitur | PDF | Word | Excel | PowerPoint |
| --- | :---: | :---: | :---: | :---: |
| Teks kaya (font, ukuran, bold/italic/underline, warna) | ✅ | ✅ | ✅ | ✅ |
| Tabel dengan header & baris belang | ✅ | ✅ | ✅ | ✅ |
| Gambar (URL, data URI, base64) | ✅ | ✅ | ✅ | ✅ |
| Grafik batang / garis / area | ✅ vektor | ✅ native | ✅ native | ✅ native |
| Grafik lingkaran / donat | ✅ vektor | ✅ native | ✅ native | ✅ native |
| Page break | ✅ | ✅ | ✅ (jarak baris) | ✅ (slide baru) |
| Nomor halaman | ✅ | – | – | ✅ |
| Orientasi & ukuran halaman | ✅ | ✅ | cetak fit-to-width | 16:9 |

## Menjalankan

Prasyarat: JDK 25 (Gradle toolchain akan memakai JDK yang tersedia) dan koneksi internet saat build pertama.

```bash
./gradlew run
```

Server berjalan di <http://localhost:7000>.

- Playground interaktif: <http://localhost:7000>
- Swagger UI: <http://localhost:7000/swagger>
- OpenAPI JSON: <http://localhost:7000/openapi>
- Contoh body JSON: <http://localhost:7000/api/documents/sample>

Port dapat diubah lewat environment variable `PORT` atau argumen pertama:

```bash
PORT=8080 ./gradlew run
./gradlew run --args="8080"
```

Build distribusi:

```bash
./gradlew build
# hasil: build/libs/*.jar dan build/distributions/
```

## Docker

Image runtime memakai distroless Debian 13 + Java 25 (nonroot, tanpa shell):

```bash
./gradlew shadowJar
docker build -t simonjp-document-generator:latest .
docker run --rm -p 7000:7000 -e PORT=7000 simonjp-document-generator:latest
```

Image juga dipublikasikan otomatis oleh workflow `CI` ke GHCR untuk setiap push ke `master`
dan tag `v*` (satu workflow: build + test jar, lalu jar di-`COPY` ke image):

```bash
docker run --rm -p 7000:7000 ghcr.io/agungdh/simonjp-document-generator:latest
```

## Library

| Kebutuhan | Library | Versi |
| --- | --- | --- |
| Dokumen PDF | Apache PDFBox | 3.0.7 |
| Word / Excel / PowerPoint | Apache POI | 5.5.1 |
| HTTP API | Javalin | 7.2.3 |
| JSON | Jackson | 2.20.1 |

PDFBox dan POI adalah library Apache yang matang dan dipakai luas. PDF digambar langsung dengan operator
PDFBox (teks, tabel, gambar, grafik vektor), sedangkan file Office memakai format Open XML native POI.

## API

Semua endpoint menerima `POST` dengan body JSON dan mengembalikan file sebagai attachment.

| Method | Path | Hasil |
| --- | --- | --- |
| `POST` | `/api/documents/pdf` | `application/pdf` |
| `POST` | `/api/documents/docx` | Word Open XML |
| `POST` | `/api/documents/xlsx` | Excel Open XML |
| `POST` | `/api/documents/pptx` | PowerPoint Open XML |
| `GET` | `/api/documents/sample` | contoh body JSON |

Contoh:

```bash
curl -X POST http://localhost:7000/api/documents/pdf \
  -H 'Content-Type: application/json' \
  -d @src/main/resources/sample-document.json \
  --output laporan.pdf
```

Bila permintaan tidak valid, server membalas `400` dengan:

```json
{ "error": "blocks[2].chart.type must be one of: pie, area, ..." }
```

Kesalahan tak terduga dibalas `500` dengan pesan generik. Ukuran body maksimum **25 MB**.

## Model dokumen

```json
{
  "title": "Laporan Penjualan 2026",
  "subtitle": "Ringkasan performa kuartal pertama",
  "author": "Tim SimonJP",
  "subject": "Contoh dokumen",
  "pageSize": "A4",
  "orientation": "portrait",
  "blocks": [
    { "type": "heading", "level": 1, "text": "Laporan Penjualan 2026" },
    {
      "type": "paragraph",
      "align": "justify",
      "runs": [
        { "text": "Teks biasa, " },
        { "text": "tebal", "bold": true },
        { "text": ", miring", "italic": true },
        { "text": ", bergaris bawah", "underline": true },
        { "text": " dan berwarna.", "color": "#C00000" }
      ]
    }
  ]
}
```

Field tingkat dokumen:

| Field | Tipe | Default | Keterangan |
| --- | --- | --- | --- |
| `title` | string | `"Untitled Document"` | Judul dokumen, juga dipakai sebagai nama file. |
| `subtitle` | string | – | Subjudul untuk slide judul PowerPoint dan metadata. |
| `author` | string | – | Metadata penulis. |
| `subject` | string | – | Metadata subjek. |
| `pageSize` | `"A4"` \| `"LETTER"` | `"A4"` | Ukuran halaman PDF/Word. |
| `orientation` | `"portrait"` \| `"landscape"` | `"portrait"` | Orientasi PDF/Word. |
| `blocks` | array | `[]` | Daftar blok, dirender berurutan. |

Semua blok memakai properti diskriminator `type`. Properti yang tidak dikenal diabaikan, sehingga aman
mengirim field tambahan.

### `heading`

```json
{ "type": "heading", "level": 2, "text": "Ringkasan Angka", "color": "#1F4E79" }
```

| Field | Default | Keterangan |
| --- | --- | --- |
| `level` | `1` | 1–4. Ukuran default 24 / 18 / 14 / 12 pt. |
| `text` | `""` | Teks judul. Baris baru didukung. |
| `align` | `left` | `left`, `center`, `right`, `justify`. |
| `color` | `#1F4E79` | Warna hex. |
| `fontFamily` | `Arial` | Nama font. |
| `fontSize` | sesuai level | 4–200 pt. |
| `marginAfter` | `10` | Jarak bawah dalam poin. |

### `paragraph`

| Field | Default | Keterangan |
| --- | --- | --- |
| `text` | – | Teks polos; dipakai bila `runs` kosong. |
| `runs` | `[]` | Potongan teks dengan gaya masing-masing. |
| `align` | `left` | `left`, `center`, `right`, `justify`. |
| `fontFamily` | `Arial` | Font dasar paragraf. |
| `fontSize` | `11` | Ukuran dasar, 4–200 pt. |
| `lineSpacing` | `1.15` | 0.5–4.0. |
| `marginAfter` | `8` | Jarak bawah dalam poin. |
| `color` | `#222222` | Warna dasar. |

Setiap `runs[]` mendukung `text`, `bold`, `italic`, `underline`, `fontFamily`, `fontSize`, dan `color`.
Properti yang tidak diisi mewarisi gaya paragraf.

### `table`

```json
{
  "type": "table",
  "headers": ["Produk", "Q1", "Q2"],
  "rows": [["Laptop", "120", "150"], ["Tablet", "90", "110"]],
  "columnWidths": [2, 1, 1],
  "headerBackground": "#1F4E79",
  "headerColor": "#FFFFFF",
  "stripeColor": "#F2F2F2",
  "fontSize": 9.5
}
```

`columnWidths` adalah bobot relatif; jumlahnya harus sama dengan jumlah kolom agar dipakai. Bila berbeda,
semua kolom dibuat sama lebar. Maksimum **12 kolom** dan **2.000 baris**.

### `image`

```json
{
  "type": "image",
  "src": "https://example.com/banner.png",
  "width": 420,
  "height": 131,
  "align": "center",
  "caption": "Gambar 1. Banner.",
  "border": true
}
```

`src` menerima URL `http(s)`, data URI (`data:image/png;base64,...`), atau string base64 mentah.
Jika hanya salah satu dari `width`/`height` diisi, rasio gambar dipertahankan. Gambar selalu diperkecil
agar muat di area konten.

### `chart`

```json
{
  "type": "chart",
  "width": 440,
  "height": 300,
  "caption": "Grafik 1. Penjualan per kuartal.",
  "chart": {
    "type": "bar",
    "title": "Penjualan per Kuartal",
    "xAxisTitle": "Kuartal",
    "yAxisTitle": "Unit",
    "categories": ["Q1", "Q2", "Q3", "Q4"],
    "series": [
      { "name": "2025", "values": [120, 150, 170, 210] },
      { "name": "2026", "values": [150, 180, 210, 260] }
    ],
    "legend": true,
    "palette": ["#2E75B6", "#ED7D31", "#70AD47", "#A5A5A5"]
  }
}
```

Tipe grafik yang didukung:

| `type` | Keterangan |
| --- | --- |
| `bar` / `column` | Batang vertikal berkelompok. |
| `horizontalBar` | Batang horizontal. |
| `stackedBar` | Batang vertikal bertumpuk. |
| `line` | Garis dengan penanda. |
| `area` | Area. |
| `stackedArea` | Area bertumpuk. |
| `pie` | Lingkaran; hanya seri pertama yang dipakai. |
| `doughnut` / `donut` / `ring` | Donat; hanya seri pertama yang dipakai. |

Nama tipe tidak peka huruf besar/kecil, spasi, `-`, dan `_`. Setiap seri harus memiliki jumlah `values`
yang sama dengan `categories`. `palette` bersifat opsional; bila kosong dipakai palet bawaan.

`fontFamily` pada `chart` memengaruhi grafik vektor PDF. Grafik native Office mengikuti theme/font aplikasi
yang membukanya.

### `pageBreak`

```json
{ "type": "pageBreak" }
```

Di PDF dan Word memaksa halaman baru. Di Excel menambah jarak dua baris; di PowerPoint memulai slide baru.

## Perilaku font

- **PDF** memakai tiga font yang dibundel di `src/main/resources/fonts`:
  **Liberation Sans**, **Liberation Serif**, dan **Liberation Mono**. Ketiganya metrik-kompatibel dengan
  Arial, Times New Roman, dan Courier New, sehingga layout tetap konsisten tanpa perlu font di server.
  Nama font lain dipetakan ke salah satu dari ketiganya berdasarkan keluarga (serif/monospace/sans).
- **Office** menyimpan nama font asli (mis. `Arial`, `Times New Roman`, `Calibri`). Jika font tidak ada di
  komputer pembuka, aplikasi akan memakai fallback-nya sendiri.
- Ukuran font dibatasi 4–200 pt. Warna memakai format `#RRGGBB`.

## Batasan

| Batasan | Nilai |
| --- | --- |
| Ukuran request | 25 MB |
| Jumlah blok per dokumen | 300 |
| Total karakter teks | 2.000.000 |
| Kolom tabel | 12 |
| Baris tabel | 2.000 |
| Kategori grafik | 200 |
| Seri grafik | 20 |
| Ukuran gambar/grafik | 10–2000 pt |

Catatan format:

- Excel memakai grid 12 kolom dengan lebar seragam dan **fit-to-width** saat dicetak. Baris paragraf yang
  sangat panjang dipecah agar tidak melewati batas tinggi baris Excel (409 pt).
- PowerPoint memakai slide 16:9 (960×540 pt) dan mengalirkan konten otomatis ke slide berikutnya.
- Perbedaan rendering antar aplikasi (Microsoft Office, LibreOffice, Google Docs) wajar terjadi pada
  detail kecil seperti jarak paragraf dan legend grafik.

## Struktur proyek

```
src/main/java/id/my/agungdh/
├── Main.java                     # Javalin, static files, OpenAPI, error handler
├── controller/DocumentController.java
└── document/
    ├── model/                    # record JSON (DocumentRequest, *Block, ChartSpec, TextRun)
    ├── render/                   # Pdf/Docx/Xlsx/PptxDocumentRenderer
    ├── service/                  # DocumentGenerator, DocumentValidator
    └── support/                  # font PDFBox, warna, gambar, chart vektor, utilitas Office
src/main/resources/
├── fonts/                        # Liberation Sans/Serif/Mono + lisensi
├── public/index.html             # playground
└── sample-document.json          # contoh lengkap semua blok
src/test/java/.../DocumentGenerationTest.java
```

## Pengujian

```bash
./gradlew test
```

Tes membuat keempat format dari `sample-document.json` ke `build/test-documents/` dan memeriksa isi paket
Office (chart native, gambar, tabel). Untuk pemeriksaan visual PDF:

```bash
pdftoppm -png build/test-documents/laporan-penjualan-2026.pdf halaman
```

File Word/Excel/PowerPoint dapat langsung dibuka di aplikasi Office atau LibreOffice; tidak ada proses
konversi di aplikasi ini.

## Lisensi font

Font Liberation dibundel dengan lisensi SIL Open Font License. Lihat
`src/main/resources/fonts/LICENSE-Liberation.txt`.
