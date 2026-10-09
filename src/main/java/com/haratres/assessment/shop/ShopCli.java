package com.haratres.assessment.shop;

import com.haratres.assessment.charactercount.CharacterCounter;
import com.haratres.assessment.console.ConsoleInput;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Console adapter for the shopping command.
 *
 * <p>Interaction flow:
 * <ol>
 *   <li>Ask how many products to enter (must be ≥ 2).
 *   <li>Collect name, price, stock, and rating for each product, retrying
 *       only the failing field on invalid input.
 *   <li>Ask for sort criterion and direction; display sorted list.
 *   <li>Ask whether the user wants to add products to cart.
 *   <li>If yes: ask product name and quantity per item; loop until Hayır.
 *   <li>Print the discounted cart total.
 * </ol>
 *
 * <p>All business rules (validation, stock) are enforced by the domain classes.
 * This class only prints prompts, reads lines, and calls domain methods.
 * Returns status 0 on normal completion, 1 on EOF or I/O failure.
 *
 * <p>Prices are formatted with {@link Locale#ROOT} so the decimal separator is
 * always '.' regardless of the JVM default locale.
 */
public final class ShopCli {
    private final CartPricer pricer;

    public ShopCli(CartPricer pricer) {
        if (pricer == null) {
            throw new IllegalArgumentException("pricer must not be null");
        }
        this.pricer = pricer;
    }

    public int run(ConsoleInput input, PrintStream out, PrintStream err) {
        ProductCatalog catalog = new ProductCatalog();

        // --- 1. Number of products ---
        int productCount = readProductCount(input, out, err);
        if (productCount < 0) {
            return 1;
        }

        // --- 2. Product details ---
        for (int i = 1; i <= productCount; i++) {
            out.println("Ürün " + i + ":");
            if (!readProduct(input, out, err, catalog)) {
                return 1;
            }
        }

        // --- 3. Sort ---
        SortField field = readSortField(input, out, err);
        if (field == null) {
            return 1;
        }
        SortOrder order = readSortOrder(input, out, err);
        if (order == null) {
            return 1;
        }
        List<Product> sorted = catalog.sortedBy(field, order);
        out.println("Sıralanmış Ürünler:");
        for (Product p : sorted) {
            out.printf(Locale.ROOT, "%s - Fiyat: %.2f, Stok: %d, Değerlendirme: %s%n",
                    p.displayName(), p.price(), p.stock(), formatRating(p.rating()));
        }

        // --- 4. Cart question ---
        Boolean wantsCart = readYesNo(input, out, err, "Sepete ürün eklemek ister misiniz? (Evet/Hayır): ");
        if (wantsCart == null) {
            return 1;
        }
        if (!wantsCart) {
            return 0;
        }

        // --- 5. Cart loop ---
        ShoppingCart cart = new ShoppingCart(catalog);
        outer:
        while (true) {
            // Read and validate product name first; if unknown, skip to "başka?" prompt.
            String productName = readCartProductName(input, out, err);
            if (productName == null) {
                return 1;
            }
            String trimmedName = productName.trim();

            if (catalog.findByName(trimmedName).isEmpty()) {
                err.println("'" + trimmedName + "' adında bir ürün bulunamadı.");
            } else {
                // Inner quantity retry loop: re-ask quantity when stock is insufficient.
                while (true) {
                    int qty = readCartQuantity(input, out, err);
                    if (qty < 0) {
                        return 1;
                    }
                    try {
                        cart.add(trimmedName, qty);
                        out.println(trimmedName + " sepetinize eklendi.");
                        break; // quantity accepted → go to "başka?" prompt
                    } catch (IllegalArgumentException ex) {
                        // Stock insufficient: show the error and re-ask quantity only.
                        err.println(ex.getMessage());
                    }
                }
            }

            // "başka?" prompt — don't allow exit until ≥ 2 distinct products are in the cart.
            while (true) {
                Boolean continueAdding = readYesNo(input, out, err,
                        "Sepete başka ürün eklemek ister misiniz? (Evet/Hayır): ");
                if (continueAdding == null) {
                    return 1;
                }
                if (continueAdding) {
                    continue outer; // go back to product name prompt
                }
                // User wants to stop — enforce minimum of 2 distinct products.
                if (cart.distinctProductCount() < 2) {
                    err.println("Sepette en az iki farklı ürün olmalıdır.");
                    // Stay in the "başka?" loop to re-prompt.
                } else {
                    break outer; // minimum met → proceed to checkout
                }
            }
        }

        // --- 6. Checkout ---
        PricingResult result = pricer.calculate(cart.items());
        out.println("Sepetiniz:");
        for (LineResult line : result.lines()) {
            out.printf(Locale.ROOT, "%s - Adet: %d, Toplam Fiyat : %.2f%n",
                    line.productName(), line.quantity(), line.lineTotal());
        }
        out.printf(Locale.ROOT, "Sepet Toplamı: %.2f%n", result.finalTotal());
        return 0;
    }

    // --- private helpers ---

    private static int readProductCount(ConsoleInput input, PrintStream out, PrintStream err) {
        while (true) {
            out.print("Kaç farklı ürün gireceksiniz: ");
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return -1;
            }
            try {
                int n = Integer.parseInt(line.trim());
                if (n >= 2) {
                    return n;
                }
                err.println("Birden fazla farklı ürün girişi yapılmalıdır.");
            } catch (NumberFormatException e) {
                err.println("Lütfen geçerli bir sayı giriniz.");
            }
        }
    }

    /**
     * Reads all fields for one product, retrying each field independently on
     * invalid input. A field is only re-asked when that specific field fails;
     * earlier fields that already passed are not repeated.
     */
    private static boolean readProduct(ConsoleInput input, PrintStream out, PrintStream err,
            ProductCatalog catalog) {
        // --- name ---
        String name;
        while (true) {
            String raw = readField(input, out, err, "Ürün Adı: ");
            if (raw == null) {
                return false;
            }
            String candidate = raw.trim();
            if (candidate.isEmpty()) {
                err.println("Ürün adı boş olamaz.");
                continue;
            }
            if (CharacterCounter.codePointLength(candidate) > 20) {
                err.println("Ürün adı en fazla 20 karakter olabilir.");
                continue;
            }
            if (catalog.findByName(candidate).isPresent()) {
                err.println("'" + candidate + "' adında bir ürün zaten mevcut.");
                continue;
            }
            name = candidate;
            break;
        }

        // --- price ---
        BigDecimal price;
        while (true) {
            String raw = readField(input, out, err, "Birim Fiyat: ");
            if (raw == null) {
                return false;
            }
            try {
                BigDecimal candidate = parseDecimal(raw.trim());
                if (candidate.compareTo(BigDecimal.ONE) < 0
                        || candidate.compareTo(new BigDecimal("100")) > 0) {
                    err.println("Birim fiyat 1 ile 100 arasında olmalıdır.");
                    continue;
                }
                price = candidate;
                break;
            } catch (IllegalArgumentException ex) {
                err.println(ex.getMessage());
            }
        }

        // --- stock ---
        int stock;
        while (true) {
            String raw = readField(input, out, err, "Stok Miktarı: ");
            if (raw == null) {
                return false;
            }
            try {
                int candidate = Integer.parseInt(raw.trim());
                if (candidate < 1) {
                    err.println("Stok miktarı en az 1 olmalıdır.");
                    continue;
                }
                stock = candidate;
                break;
            } catch (NumberFormatException ex) {
                err.println("Lütfen geçerli bir sayı giriniz.");
            }
        }

        // --- rating ---
        BigDecimal rating;
        while (true) {
            String raw = readField(input, out, err, "Değerlendirme Puanı: ");
            if (raw == null) {
                return false;
            }
            try {
                BigDecimal candidate = parseDecimal(raw.trim());
                if (candidate.compareTo(BigDecimal.ZERO) < 0
                        || candidate.compareTo(new BigDecimal("5")) > 0) {
                    err.println("Değerlendirme puanı 0 ile 5 arasında olmalıdır.");
                    continue;
                }
                rating = candidate;
                break;
            } catch (IllegalArgumentException ex) {
                err.println(ex.getMessage());
            }
        }

        catalog.add(new Product(name, price, stock, rating));
        return true;
    }

    private static SortField readSortField(ConsoleInput input, PrintStream out, PrintStream err) {
        while (true) {
            out.println("Ürünleri hangi kritere göre sıralamak istersiniz? (name/stock/rating)");
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return null;
            }
            String trimmed = line.trim().toLowerCase(Locale.ROOT);
            switch (trimmed) {
                case "name"   -> {
                    out.println("Seçilen Kriter: name");
                    return SortField.NAME;
                }
                case "stock"  -> {
                    out.println("Seçilen Kriter: stock");
                    return SortField.STOCK;
                }
                case "rating" -> {
                    out.println("Seçilen Kriter: rating");
                    return SortField.RATING;
                }
                default -> err.println("Lütfen name, stock veya rating giriniz.");
            }
        }
    }

    private static SortOrder readSortOrder(ConsoleInput input, PrintStream out, PrintStream err) {
        while (true) {
            out.println("Sıralama türü artan mı azalan mı olsun? (artan/azalan)");
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return null;
            }
            String trimmed = line.trim().toLowerCase(Locale.ROOT);
            switch (trimmed) {
                case "artan"  -> {
                    out.println("Seçilen Sıralama Türü: artan");
                    return SortOrder.ASC;
                }
                case "azalan" -> {
                    out.println("Seçilen Sıralama Türü: azalan");
                    return SortOrder.DESC;
                }
                default -> err.println("Lütfen artan veya azalan giriniz.");
            }
        }
    }

    private static String readCartProductName(ConsoleInput input, PrintStream out, PrintStream err) {
        out.print("Eklemek istediğiniz ürünün adı: ");
        out.flush();
        return readLineOrNull(input, err);
    }

    private static int readCartQuantity(ConsoleInput input, PrintStream out, PrintStream err) {
        while (true) {
            out.print("Eklemek istediğiniz adet: ");
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return -1;
            }
            try {
                int qty = Integer.parseInt(line.trim());
                if (qty > 0) {
                    return qty;
                }
                err.println("Adet sıfırdan büyük olmalıdır.");
            } catch (NumberFormatException e) {
                err.println("Lütfen geçerli bir sayı giriniz.");
            }
        }
    }

    private static Boolean readYesNo(ConsoleInput input, PrintStream out, PrintStream err, String prompt) {
        while (true) {
            out.print(prompt);
            out.flush();
            String line = readLineOrNull(input, err);
            if (line == null) {
                return null;
            }
            String trimmed = line.trim();
            if ("Evet".equalsIgnoreCase(trimmed)) {
                return Boolean.TRUE;
            }
            if ("Hayır".equalsIgnoreCase(trimmed) || "Hayir".equalsIgnoreCase(trimmed)) {
                return Boolean.FALSE;
            }
            err.println("Lütfen geçerli bir cevap giriniz.");
        }
    }

    private static String readField(ConsoleInput input, PrintStream out, PrintStream err, String prompt) {
        out.print(prompt);
        out.flush();
        return readLineOrNull(input, err);
    }

    /**
     * Formats a rating, always showing at least one decimal place.
     * 4.5 → "4.5", 4.0 → "4.0", 5 → "5.0"
     */
    static String formatRating(BigDecimal rating) {
        BigDecimal stripped = rating.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.toPlainString() + ".0";
        }
        return stripped.toPlainString();
    }

    /**
     * Parses a decimal string, accepting only '.' as the decimal separator.
     * Commas are rejected to avoid silent misparsing.
     */
    static BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Sayısal değer boş olamaz.");
        }
        if (s.contains(",")) {
            throw new IllegalArgumentException(
                    "Ondalık ayracı olarak '.' kullanınız (virgül kabul edilmez).");
        }
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Geçerli bir sayı giriniz: " + s);
        }
    }

    private static String readLineOrNull(ConsoleInput input, PrintStream err) {
        try {
            String line = input.readLine();
            if (line == null) {
                err.println("Giriş sona erdi.");
            }
            return line;
        } catch (IOException e) {
            err.println("Okuma hatası: " + e.getMessage());
            return null;
        }
    }
}
