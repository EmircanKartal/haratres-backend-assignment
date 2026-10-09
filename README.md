# Haratres Backend Assignment

Java 17 console project implementing the character-counting and product-management exercises.

**Status: both commands implemented and tested.**

## Build and run

Requires JDK 17. Maven Wrapper downloads the pinned Maven distribution; a separate installation is not needed.

```sh
chmod +x mvnw
./mvnw clean verify
java -jar target/assessment.jar --help
```

On Windows use `mvnw.cmd clean verify`.

## Question 1 — character counting

```sh
java -jar target/assessment.jar count
```

Sample session (matching the assignment example):

```
Maksimum karakter sayısı belirleyin: 50
Lütfen bir cümle girin: Merhaba Dünya!
Büyük/küçük harf duyarlılığı aktif olsun mu? (Evet/Hayır): Hayır
Analiz etmek için bir harf girin: a
Girilen cümlede 'a' harfi toplamda 3 defa geçmektedir.
```

- Sentence length is measured in Unicode code points (not UTF-16 units).
- Case-insensitive mode uses Turkish rules: `I`/`ı` share one bucket, `İ`/`i` share another.
- Spaces and punctuation are counted.
- Invalid answers at any step print a Turkish error message and retry.
- EOF at any step exits with status 1.

## Question 2 — product management

```sh
java -jar target/assessment.jar shop
```

Sample session (matching the assignment example):

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

- At least 2 products must be entered.
- Price must be between 1 and 100; stock must be at least 1; name at most 20 characters; rating 0–5.
- Sorting does not affect cart insertion order.
- Stock is validated on each cart addition; cumulative quantities are checked.
- Discount rule: if a cart entry's unit price exceeds the next entry's, the difference is the discounted price. Last entry is unchanged. See [design decisions](docs/decisions.md).

## Docker

```sh
docker build -t haratres-assessment .
docker run --rm haratres-assessment --help
docker run --rm -it haratres-assessment count
docker run --rm -it haratres-assessment shop
```

## Architecture

One Maven module, organized by feature:

- `charactercount`: `CharacterCounter` (pure counting logic) and `CharacterCounterCli` (prompts).
- `shop`: `Product`, `ProductCatalog`, `CartItem`, `ShoppingCart`, `CartPricer`, `ShopCli`.
- `console`: `ConsoleInput` (shared line reader).
- `Main`: command selection and object wiring.

Business logic classes contain no input/output. CLI adapters own all prompts and messages. Dependencies are passed through constructors.

## Tests

77 JUnit 5 tests covering:
- Character counting: occurrence counting, Turkish i pairs, supplementary code points, zero matches, validation.
- CLI flows: happy paths, invalid-input recovery, EOF at every step.
- Products: all constraints, duplicate-name detection, Turkish case-insensitive lookup.
- Sorting: all three fields and both directions, tiebreaking, catalog immutability.
- Cart: merging, stock validation, atomic rejection on exceeded stock, preserved insertion order.
- Pricing: assignment discount example, three-entry scenario, equal/increasing prices, repeatable totals.

See [design decisions](docs/decisions.md) for assumptions and tradeoffs.
