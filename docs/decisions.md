# Design decisions

## Scope

Java 17, Maven Wrapper, JUnit 5, one executable JAR, two console commands.
Business logic classes contain no input/output. CLI adapters own all prompts and messages.
Concrete classes are used throughout; no interface per class and no dependency injection framework.

---

## Question 1 — character counting

### What changed from the scaffold

The initialized scaffold printed a histogram (count of every character). That does not match
the assignment. The current implementation runs a 4-step conversation: maximum character count →
sentence → case sensitivity (Evet/Hayır) → search character → print occurrences.

`CharacterCount.java` (the histogram record) was deleted. `CharacterCounter` now exposes
`countOccurrences(text, searchCodePoint, caseSensitive)` only.

### Turkish case matching

Folding uses `Locale.forLanguageTag("tr")` and is independent of the JVM default locale.
Verified on JDK 17:

- `I` (U+0049) lowercases to dotless `ı` (U+0131). `ı` stays `ı`.
- `İ` (U+0130) lowercases to dotted `i` (U+0069). `i` stays `i`.
- English/root locales lowercase `I` to dotted `i`, and `İ` to `i` plus a combining dot (U+0307).

Turkish has two distinct i letters. Case-insensitive mode merges `I`/`ı` and merges `İ`/`i`.
`Character.toLowerCase(int)` ignores locale and maps `I` to `i`, so it is not used.
Each code point is folded independently. `ş/Ş`, `ğ/Ğ`, `ü/Ü`, `ö/Ö`, and `ç/Ç` are
one-to-one under both locales; the locale choice matters only for the two i pairs.

### Unicode code-point counting

`String.length()` counts UTF-16 code units. An emoji such as 😀 is one scalar and two `char`
values, so a `char` loop would split it. The counter and length helper walk `codePoints()`.
Precomposed `é` (U+00E9) counts once. Decomposed `e` + combining acute (U+0301) counts as two
code points; grapheme-cluster counting is not the unit here (it depends on Unicode version and
complicates case folding).

### NFC normalization on input

`ConsoleInput.readLine()` NFC-normalizes every line before returning it. This means a `Ç`
pasted from macOS as C + U+0327 (NFD decomposition) is treated as the single precomposed
code point U+00C7, consistent with direct keyboard input. Without this, the character would
be counted as two code points and would fail the single-character validation.

### "Merhaba Dünya!" example

Counting lowercase `a` case-sensitively: M,e,r,h,**a**,b,**a**,' ',D,ü,n,y,**a**,! = **3**.
The assignment example says 3 and that is correct.
A ChatGPT-authored intermediary spec incorrectly stated 4; that claim is disregarded.

### Character search loop

The assignment example shows one search character. **Assumption:** the program keeps asking
for a character to analyse indefinitely until the user signals end of input (Ctrl+D on
macOS/Linux, Ctrl+Z then Enter on Windows). An exit hint is printed before the first prompt.
Exit status is 0 if at least one character was analysed; 1 if EOF arrived before any result.

### Sentence and product-name length

Measured in Unicode code points, not UTF-16 code units. The assignment says "20 karakter";
code points are the technically accurate measure and results are identical for all BMP text.

---

## Question 2 — product management

### Product constraints

- Name: non-blank, at most 20 code points (**assumption:** code points, not grapheme clusters).
- Price: 1 through 100 inclusive, two decimal places displayed. Constructed from `String`,
  never from `double`.
- Stock: at least 1.
- Rating: 0 through 5 inclusive, decimals allowed.
  The assignment says "5 üzerinden"; lower bound of 0 is an **assumption**.

### Per-field retry on invalid input

Each product field is retried independently on invalid input. An invalid price re-asks
`Birim Fiyat:` only — the product name and previously accepted fields are not repeated.
This is the most user-friendly interpretation of "uyarı verip tekrar girilmesi istenmelidir."

### Decimal separator

Only `.` is accepted for numeric input. Input containing `,` is rejected with a message.
The assignment does not specify; using `,` as a separator would cause silent misparsing.
Display uses `Locale.ROOT` in all `printf` calls, so the decimal separator is always `.`
regardless of the JVM default locale or the host operating system. A Turkish-locale JVM
would otherwise format `3.00` as `3,00`.

### Sort prompt format

After the user types a valid sort criterion or direction, the program echoes the selection
on its own line (`Seçilen Kriter: rating`, `Seçilen Sıralama Türü: azalan`), matching the
assignment's example program flow exactly.

### Duplicate product names

Names are compared via Turkish case-insensitive folding of each code point.
"Defter" and "defter" and "DEFTER" are all the same product.
The folded key is used internally; the display name preserves the user's casing.

### Sorting

`ProductCatalog.sortedBy()` returns a new `List<Product>` and never mutates the catalog.
Cart insertion order is always preserved regardless of sorting.
Name ties use a Turkish `Collator` at `IDENTICAL` strength.
Stock and rating ties use the Turkish name as a secondary key.

### Cart minimum

The assignment says "sepette en az iki ürün olacak şekilde" (at least two products in the
cart). This is interpreted as **two distinct product types** (`distinctProductCount() ≥ 2`),
not two total units. If the user attempts to end cart entry with only one distinct product,
an error is printed and the cart prompt continues — the user is not hard-exited.

### Cart: unknown product name

When the entered name does not match any catalog product, the error is printed and the
program goes directly to the "başka?" continuation prompt — it does **not** ask for a
quantity. Quantity is only requested once the name is confirmed to exist.

### Cart: insufficient stock → re-ask quantity

The assignment says: "stokta yeterli ürün olmadığı belirtilir ve kullanıcıdan yeni bir
**adet** girmesi istenir." When stock is insufficient, only the quantity prompt is repeated
for the same product. The product name is not re-asked.

### Cart stock validation

Cart quantities are validated against the original catalog stock each time an item is added.
The catalog's `stock` field is never decremented; the cart tracks cumulative requested quantities.
A rejected addition leaves the cart unchanged (no partial mutation).

### Discount rule

**Assumption — not confirmed by the assignment owner for carts with more than two entries.**

The assignment example covers exactly two products. The following rule is applied to carts
with more entries:

- Compare adjacent cart entries in insertion order using their **original** unit prices.
- If entry `i`'s unit price is greater than entry `i+1`'s unit price, the discounted unit
  price for entry `i` is `price[i] − price[i+1]`.
- The last entry is always unchanged.
- Catalog prices are never mutated.
- Discounted prices do not cascade into subsequent comparisons.
- Repeated calls to `CartPricer.calculate` with the same cart produce identical results.

Verified examples:
- Defter (2 × 3.00) → Kalem (2 × 1.50): Defter unit becomes 1.50, total **6.00**. ✅
- Three entries 10.00, 6.00, 2.00 qty 1 each: discounted prices 4.00, 4.00, 2.00; total 10.00.
- Equal or increasing adjacent prices: no discount applied.

### Money arithmetic

All monetary values use `BigDecimal` constructed from `String`. Rounding is `HALF_UP` to two
decimal places. No floating-point literals are used in price calculations.

---

## Cross-platform encoding

`System.out`, `System.err`, and `System.in` are all wrapped as explicit `UTF-8` in
`Main.main()` so Turkish characters display and are read correctly on Windows (where the JVM
default encoding is often `windows-1252` or `CP437`). The Docker image sets `LANG=C.UTF-8`
and passes `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` directly
on the `java` command line.

---

## Verification

`./mvnw clean verify` — **124 tests, 0 failures, BUILD SUCCESS** on JDK 17.
`docker build -t haratres-assessment .` — builds and runs all 124 tests inside the container,
verified on Docker Desktop (Linux/amd64). Interactive smoke tests confirm Turkish character
output and correct totals in both `count` and `shop` commands.
