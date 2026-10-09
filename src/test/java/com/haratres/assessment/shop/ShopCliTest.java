package com.haratres.assessment.shop;

import com.haratres.assessment.console.ConsoleInput;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ShopCli.
 *
 * <p>Every test drives the full CLI through a scripted stdin string, then
 * inspects the captured stdout / stderr and exit status.
 *
 * <p>Test groupings:
 * <ul>
 *   <li>Happy-path (assignment example + full flows)
 *   <li>Product count validation
 *   <li>Per-field retry (one bad value → only that field re-asked)
 *   <li>Sort criteria and display format
 *   <li>Cart: adding, merging, declining
 *   <li>Cart: stock insufficient → quantity re-asked (not product name)
 *   <li>Cart: unknown product name → quantity NOT asked; go to "başka?" prompt
 *   <li>Cart: minimum 2 distinct products enforced
 *   <li>Checkout total and discount
 *   <li>EOF / I/O error exits
 *   <li>Static helpers: parseDecimal, formatRating
 * </ul>
 */
class ShopCliTest {
    private final ShopCli cli = new ShopCli(new CartPricer());

    // ─── Happy path: assignment example ──────────────────────────────────────

    /**
     * Full run matching the assignment's example flow exactly.
     * Defter (price 3.00) → Kalem (price 1.50) → discount 3.00 → 1.50 per unit.
     * Expected total: 2×1.50 + 2×1.50 = 6.00.
     */
    @Test void assignmentExampleProducesCorrectTotal() {
        String input = lines(
                "2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "rating", "azalan",
                "Evet",
                "Defter", "2",
                "Evet",
                "Kalem", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, "stderr: " + c.errors);
        assertTrue(c.output.contains("Sepet Toplamı: 6.00"), c.output);
        assertTrue(c.output.contains("Defter"), c.output);
        assertTrue(c.output.contains("Kalem"), c.output);
    }

    /**
     * Assignment example: sort prompts must echo the chosen value on their
     * own line as specified ("Seçilen Kriter: rating", "Seçilen Sıralama Türü: azalan").
     */
    @Test void sortPromptsEchoSelectedValues() {
        String input = lines(
                "2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "rating", "azalan",
                "Hayır");
        Capture c = run(input);
        assertTrue(c.output.contains("Seçilen Kriter: rating"), c.output);
        assertTrue(c.output.contains("Seçilen Sıralama Türü: azalan"), c.output);
    }

    @Test void sortByNameAscendingEchoesArtan() {
        String input = lines(
                "2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Seçilen Kriter: name"), c.output);
        assertTrue(c.output.contains("Seçilen Sıralama Türü: artan"), c.output);
    }

    // ─── Product count validation ─────────────────────────────────────────────

    @Test void productCountLessThanTwoIsRetried() {
        String input = lines("1", "2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("Birden fazla"), c.errors);
    }

    @Test void nonNumericProductCountIsRetried() {
        String input = lines("abc", "2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("geçerli bir sayı"), c.errors);
    }

    // ─── Per-field retry ──────────────────────────────────────────────────────

    @Test void invalidPriceRetriesOnlyPriceField() {
        // name "Kalem" entered once; "1,50" (comma) fails → "1.50" accepted;
        // stock + rating proceed normally — name NOT re-asked.
        String input = lines("2",
                "Kalem", "1,50", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("virgül kabul edilmez"), c.errors);
        assertTrue(c.output.contains("Kalem"), c.output);
    }

    @Test void priceAtLowerBoundary_1_00_isAccepted() {
        String input = lines("2",
                "A", "1.00", "10", "4.5",
                "B", "5.00", "10", "4.5",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Fiyat: 1.00"), c.output);
    }

    @Test void priceAtUpperBoundary_100_00_isAccepted() {
        String input = lines("2",
                "A", "100.00", "10", "4.5",
                "B", "5.00",   "10", "4.5",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Fiyat: 100.00"), c.output);
    }

    @Test void priceBelowMinimumIsRetried() {
        // 0.99 < 1.00 → error; then 2.00 accepted
        String input = lines("2",
                "Kalem", "0.99", "2.00", "10", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("1 ile 100"), c.errors);
    }

    @Test void priceAboveMaximumIsRetried() {
        String input = lines("2",
                "Kalem", "100.01", "2.00", "10", "4.5",
                "Defter", "3.00",  "50",   "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("1 ile 100"), c.errors);
    }

    @Test void invalidStockRetriesOnlyStockField() {
        String input = lines("2",
                "Kalem", "1.50", "0", "10", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("en az 1"), c.errors);
        assertTrue(c.output.contains("Kalem"), c.output);
    }

    @Test void stockAtMinimumBoundary_1_isAccepted() {
        String input = lines("2",
                "Kalem", "1.50", "1", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Stok: 1"), c.output);
    }

    @Test void invalidRatingRetriesOnlyRatingField() {
        String input = lines("2",
                "Kalem", "1.50", "100", "6", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("0 ile 5"), c.errors);
    }

    @Test void ratingAtLowerBoundary_0_isAccepted() {
        String input = lines("2",
                "A", "5.00", "10", "0",
                "B", "5.00", "10", "5",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Değerlendirme: 0.0"), c.output);
    }

    @Test void ratingAtUpperBoundary_5_isAccepted() {
        String input = lines("2",
                "A", "5.00", "10", "0",
                "B", "5.00", "10", "5",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Değerlendirme: 5.0"), c.output);
    }

    @Test void nameTooLongRetriesOnlyNameField() {
        String longName = "A".repeat(21);
        String input = lines("2",
                longName, "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("en fazla 20"), c.errors);
        assertTrue(c.output.contains("Kalem"), c.output);
    }

    @Test void nameAtExactLimit_20CodePoints_isAccepted() {
        String name20 = "A".repeat(20);
        String input = lines("2",
                name20, "1.50", "10", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains(name20), c.output);
    }

    @Test void duplicateNameRetriesOnlyNameField() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Kalem", "Defter", "3.00", "50", "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("zaten mevcut"), c.errors);
        assertTrue(c.output.contains("Defter"), c.output);
    }

    // ─── Sort ─────────────────────────────────────────────────────────────────

    @Test void invalidSortFieldIsRetried() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "fiyat", "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("name, stock veya rating"), c.errors);
    }

    @Test void invalidSortOrderIsRetried() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "karışık", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.errors.contains("artan veya azalan"), c.errors);
    }

    @Test void sortedListAlwaysUsesDecimalPoint() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertTrue(c.output.contains("Fiyat: 1.50"), c.output);
        assertTrue(c.output.contains("Fiyat: 3.00"), c.output);
    }

    @Test void ratingDisplayShowsOneDecimalForWholeNumbers() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.0",
                "Defter", "3.00", "50",  "4.0",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertTrue(c.output.contains("4.0"), c.output);
        assertFalse(c.output.matches("(?s).*Değerlendirme: 4[^.].*"), c.output);
    }

    @Test void sortByStockDescendingOrdersCorrectly() {
        // Kalem stock=100, Defter stock=50 → DESC: Kalem first
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "stock", "azalan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        int kalemPos = c.output.indexOf("Kalem");
        int defterPos = c.output.indexOf("Defter");
        // Kalem (100) must appear before Defter (50) in descending stock order
        assertTrue(kalemPos < defterPos,
                "Expected Kalem before Defter in descending stock order: " + c.output);
    }

    // ─── Cart: decline / enter ────────────────────────────────────────────────

    @Test void decliningCartSkipsCheckout() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan", "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertFalse(c.output.contains("Sepet Toplamı"), c.output);
    }

    @Test void samProductAddedTwiceMergesQuantity() {
        // Kalem added 3 + 4 = 7 units; Defter added 2 units.
        String input = lines("2",
                "Kalem", "1.50", "10", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan",
                "Evet", "Kalem", "3",
                "Evet", "Kalem", "4",
                "Evet", "Defter", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Kalem - Adet: 7"), c.output);
        assertTrue(c.output.contains("Defter - Adet: 2"), c.output);
    }

    // ─── Cart: insufficient stock → re-ask quantity only ─────────────────────

    /**
     * The assignment says: "stokta yeterli ürün olmadığı belirtilir ve
     * kullanıcıdan yeni bir adet girmesi istenir."
     * Qty 10 exceeds stock 5 → error → re-ask qty → 3 accepted.
     * Product name must NOT be re-asked.
     */
    @Test void insufficientStockReAsksQuantityNotProductName() {
        String input = lines("2",
                "Kalem", "1.50", "5", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan",
                "Evet",
                "Kalem", "10",  // qty 10 > stock 5 → error
                "3",            // re-asked qty (product name NOT re-prompted)
                "Evet",
                "Defter", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, "stderr: " + c.errors);
        assertTrue(c.errors.contains("yeterli"), c.errors);   // stock error printed
        assertTrue(c.output.contains("Kalem - Adet: 3"), c.output); // qty=3 in cart
        assertTrue(c.output.contains("Sepet Toplamı"), c.output);
    }

    @Test void multipleInsufficientStockAttemptsAllRetried() {
        // stock = 5; tries 10, 8, 3 — only 3 succeeds
        String input = lines("2",
                "Kalem", "1.50", "5", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan",
                "Evet",
                "Kalem", "10", "8", "3",
                "Evet",
                "Defter", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, "stderr: " + c.errors);
        assertTrue(c.output.contains("Kalem - Adet: 3"), c.output);
    }

    // ─── Cart: unknown product name ───────────────────────────────────────────

    /**
     * Unknown product name → print "bulunamadı" error.
     * Quantity must NOT be asked for the unknown product.
     * User can then add valid products and reach checkout.
     */
    @Test void unknownProductInCartPrintsErrorWithoutAskingQty() {
        // After "Silgi" error → "başka?" prompt reads "Evet" → proceed to add valid items.
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "name", "artan",
                "Evet",
                "Silgi",       // unknown — NO qty line follows
                "Evet",        // continue (this is the "başka?" prompt answer)
                "Kalem", "2",
                "Evet",
                "Defter", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, "stderr: " + c.errors);
        assertTrue(c.errors.contains("bulunamadı"), c.errors);
        assertTrue(c.output.contains("Sepet Toplamı"), c.output);
    }

    // ─── Cart: minimum 2 distinct products ───────────────────────────────────

    /**
     * Adding 1 unit of 1 product then saying Hayır must print a warning and
     * keep the cart loop open (NOT exit with status 1).
     */
    @Test void cartWithOnlyOneDistinctProductIsRejectedOnHayir() {
        // Only Kalem in cart; user says Hayır → error + jump straight to product name prompt.
        // No redundant yes/no re-ask — Defter name is next in input.
        String input = lines("2",
                "Kalem", "1.50", "10", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan",
                "Evet",
                "Kalem", "1",
                "Hayır",    // rejected: only 1 distinct product → error → product name prompt
                "Defter", "2",  // no "Evet" needed; directly asked for product name
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, "stderr: " + c.errors);
        assertTrue(c.errors.contains("en az iki farklı ürün"), c.errors);
        assertTrue(c.output.contains("Sepet Toplamı"), c.output);
    }

    @Test void cartWithTwoDifferentProductsOf1UnitEachIsAllowed() {
        // 1 unit Kalem + 1 unit Defter = 2 distinct products → checkout OK
        String input = lines("2",
                "Kalem", "1.50", "10", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan",
                "Evet",
                "Kalem", "1",
                "Evet",
                "Defter", "1",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, "stderr: " + c.errors);
        assertTrue(c.output.contains("Sepet Toplamı"), c.output);
    }

    // ─── Checkout: discount and totals ───────────────────────────────────────

    @Test void checkoutTotalAlwaysUsesDecimalPoint() {
        String input = lines("2",
                "Kalem", "1.50", "100", "4.5",
                "Defter", "3.00", "50",  "4.7",
                "rating", "azalan",
                "Evet", "Defter", "2",
                "Evet", "Kalem",  "2",
                "Hayır");
        Capture c = run(input);
        assertTrue(c.output.contains("Sepet Toplamı: 6.00"), c.output);
        assertFalse(c.output.contains("6,00"), c.output);
    }

    @Test void equalPricesProduceNoDiscount() {
        // Both products at 2.00 → no discount → total = 4×2.00 = 8.00
        String input = lines("2",
                "Kalem", "2.00", "10", "4.5",
                "Defter", "2.00", "10", "4.5",
                "name", "artan",
                "Evet", "Kalem",  "2",
                "Evet", "Defter", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Sepet Toplamı: 8.00"), c.output);
    }

    @Test void cheaperFirstProductGetsNoDiscountLastIsAlwaysUndiscounted() {
        // Kalem 1.50 (first) < Defter 3.00 (second) → no discount on Kalem;
        // Defter (last) also undiscounted → total = 2×1.50 + 2×3.00 = 9.00
        String input = lines("2",
                "Kalem", "1.50", "10", "4.5",
                "Defter", "3.00", "10", "4.7",
                "name", "artan",
                "Evet", "Kalem",  "2",
                "Evet", "Defter", "2",
                "Hayır");
        Capture c = run(input);
        assertEquals(0, c.status, c.errors);
        assertTrue(c.output.contains("Sepet Toplamı: 9.00"), c.output);
    }

    // ─── EOF / I/O error exits ────────────────────────────────────────────────

    @Test void eofAtProductCountExitsOne() {
        Capture c = run("");
        assertEquals(1, c.status);
    }

    @Test void eofDuringProductEntryExitsOne() {
        Capture c = run("2\nKalem\n"); // EOF mid-product
        assertEquals(1, c.status);
    }

    @Test void eofDuringCartQuantityExitsOne() {
        // Full product entry + valid sort; EOF while entering qty
        String input = lines("2",
                "Kalem", "1.50", "5", "4.5",
                "Defter", "3.00", "50", "4.7",
                "name", "artan",
                "Evet", "Kalem"); // no qty line
        Capture c = run(input);
        assertEquals(1, c.status);
    }

    // ─── parseDecimal ─────────────────────────────────────────────────────────

    @Test void parseDecimalRejectsComma() {
        assertThrows(IllegalArgumentException.class, () -> ShopCli.parseDecimal("1,50"));
    }

    @Test void parseDecimalRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> ShopCli.parseDecimal("  "));
    }

    @Test void parseDecimalRejectsNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> ShopCli.parseDecimal("abc"));
    }

    @Test void parseDecimalAcceptsDot() {
        assertEquals(new BigDecimal("1.50"), ShopCli.parseDecimal("1.50"));
    }

    @Test void parseDecimalRoundsToTwoPlaces() {
        // 1.999 rounded HALF_UP to 2 decimal places = 2.00
        assertEquals(new BigDecimal("2.00"), ShopCli.parseDecimal("1.999"));
    }

    // ─── formatRating ─────────────────────────────────────────────────────────

    @Test void formatRatingKeepsOneDecimalForWhole() {
        assertEquals("4.0", ShopCli.formatRating(new BigDecimal("4.0")));
        assertEquals("5.0", ShopCli.formatRating(new BigDecimal("5")));
    }

    @Test void formatRatingPreservesSignificantDecimals() {
        assertEquals("4.7", ShopCli.formatRating(new BigDecimal("4.70")));
        assertEquals("4.5", ShopCli.formatRating(new BigDecimal("4.5")));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Capture run(String input) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = cli.run(
                new ConsoleInput(new StringReader(input)),
                utf8(out),
                utf8(err));
        return new Capture(status,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private static PrintStream utf8(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    private static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private record Capture(int status, String output, String errors) { }
}
