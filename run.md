# How to build and run

## Prerequisites

| Platform | JDK | Terminal encoding |
|---|---|---|
| macOS | JDK 17 (Homebrew or Temurin) | UTF-8 by default ✓ |
| Linux | JDK 17 (any distribution) | UTF-8 by default ✓ |
| Windows | JDK 17 (Temurin recommended) | See note below |

### Windows UTF-8 terminal (required for Turkish characters)

The JAR sends UTF-8 bytes for all output. You also need your terminal to display UTF-8:

- **Windows Terminal** (recommended) — defaults to UTF-8, no extra steps.
- **CMD** — run `chcp 65001` once before starting the JAR.
- **PowerShell 7+** — run `[Console]::OutputEncoding = [Text.Encoding]::UTF8` once.
- **Git Bash / WSL** — UTF-8 by default ✓.

No separate Maven installation is needed. The Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads Maven automatically.

---

## 1. Build

**macOS / Linux:**
```sh
chmod +x mvnw
./mvnw clean verify
```

**Windows:**
```cmd
mvnw.cmd clean verify
```

This compiles, runs all 100 tests, and produces `target/assessment.jar`.

---

## 2. Question 1 — Character counting

```sh
java -jar target/assessment.jar count
```

### Assignment example session

```
Maksimum karakter sayısı belirleyin: 50
Lütfen bir cümle girin: Merhaba Dünya!
Büyük/küçük harf duyarlılığı aktif olsun mu? (Evet/Hayır): Hayır
Analiz etmek için bir harf girin: a
Girilen cümlede 'a' harfi toplamda 3 defa geçmektedir.
```

### Validation behavior

| Situation | What happens |
|---|---|
| Sentence exceeds the max character count | Error printed, sentence prompt repeats |
| Answer to case-sensitivity is not Evet/Hayır | "Lütfen geçerli bir cevap giriniz." — prompt repeats |
| Search character field is empty | "Geçerli bir karakter giriniz." — prompt repeats |
| More than one character entered | "Geçerli bir karakter giriniz." — prompt repeats |
| End of input (Ctrl+D / Ctrl+Z) at any step | Exits with status 1 |

### Character counting rules

- Sentence and character length measured in **Unicode code points** (not UTF-16 units). An emoji counts as 1 character regardless of byte size.
- Input is **NFC-normalized** on read, so `Ç` typed or pasted as a decomposed form (C + combining cedilla) is treated as one character.
- Case-insensitive mode uses **Turkish rules**: `I`/`ı` share a bucket, `İ`/`i` share a separate bucket. The JVM default locale is never consulted.
- Spaces and punctuation are counted.

---

## 3. Question 2 — Product management

```sh
java -jar target/assessment.jar shop
```

### Assignment example session

```
Kaç farklı ürün gireceksiniz: 2
Ürün 1:
Ürün Adı: Kalem
Birim Fiyat: 1.50
Stok Miktarı: 100
Değerlendirme Puanı: 4.5
Ürün 2:
Ürün Adı: Defter
Birim Fiyat: 3.00
Stok Miktarı: 50
Değerlendirme Puanı: 4.7
Ürünleri hangi kritere göre sıralamak istersiniz? (name/stock/rating): rating
Sıralama türü artan mı azalan mı olsun? (artan/azalan): azalan
Sıralanmış Ürünler:
Defter - Fiyat: 3.00, Stok: 50, Değerlendirme: 4.7
Kalem - Fiyat: 1.50, Stok: 100, Değerlendirme: 4.5
Sepete ürün eklemek ister misiniz? (Evet/Hayır): Evet
Eklemek istediğiniz ürünün adı: Defter
Eklemek istediğiniz adet: 2
Defter sepetinize eklendi.
Sepete başka ürün eklemek ister misiniz? (Evet/Hayır): Evet
Eklemek istediğiniz ürünün adı: Kalem
Eklemek istediğiniz adet: 2
Kalem sepetinize eklendi.
Sepete başka ürün eklemek ister misiniz? (Evet/Hayır): Hayır
Sepetiniz:
Defter - Adet: 2, Toplam Fiyat : 3.00
Kalem - Adet: 2, Toplam Fiyat : 3.00
Sepet Toplamı: 6.00
```

### Product input rules

| Field | Rule |
|---|---|
| Ürün sayısı | Must be ≥ 2, otherwise prompted again |
| Ürün Adı | Non-blank, at most 20 characters; duplicate names rejected (case-insensitive) |
| Birim Fiyat | 1 to 100 inclusive; use `.` as decimal separator — comma is rejected |
| Stok Miktarı | At least 1 |
| Değerlendirme Puanı | 0 to 5 inclusive, decimals allowed |

Each field is retried independently on invalid input. An invalid price only re-asks `Birim Fiyat:`, not the product name.

### Sort options

- Criterion: `name` · `stock` · `rating`
- Direction: `artan` (ascending) · `azalan` (descending)

### Cart and discount

- Type `Hayır` at the first cart prompt to skip checkout entirely.
- Unknown product name or insufficient stock prints an error; the cart is not modified.
- Discount rule: if an entry's unit price exceeds the next entry's, that difference becomes the discounted unit price. Last cart entry is never discounted.
  - Example: Defter 3.00 → Kalem 1.50: Defter sold at 1.50, total 6.00.
- Prices always display with `.` as decimal separator regardless of the system locale.

---

## 4. Help

```sh
java -jar target/assessment.jar --help
```

---

## 5. Run tests only (without rebuilding the JAR)

**macOS / Linux:**
```sh
./mvnw test
```

**Windows:**
```cmd
mvnw.cmd test
```

---

## 6. Docker (requires Docker Desktop to be running)

Works identically on macOS, Linux, and Windows — the container is Linux-based.

```sh
docker build -t haratres-assessment .
docker run --rm haratres-assessment --help
docker run --rm -it haratres-assessment count
docker run --rm -it haratres-assessment shop
```

No encoding setup needed inside Docker — the container uses UTF-8 natively.

---

## Cross-platform notes

| Concern | How it is handled |
|---|---|
| Turkish character output (`ç ş ü ı ğ ö İ Ç Ş Ü Ğ Ö`) | `System.out`/`System.err` wrapped as explicit UTF-8 in `Main.main()` |
| Turkish character input | `System.in` read as explicit UTF-8 |
| NFD-decomposed characters (pasted `Ç` on macOS) | `ConsoleInput` NFC-normalizes every line |
| Decimal separator in prices | `Locale.ROOT` used in all `printf` calls — always `.` |
| Turkish case folding | `Locale.forLanguageTag("tr")` used explicitly — JVM default locale ignored |
| Line endings | `.gitattributes` sets `eol=lf` for all text files; `mvnw.cmd` uses `eol=crlf` |
| `mvnw` executable bit | Set in `.gitattributes`; CI runs `chmod +x mvnw` |
