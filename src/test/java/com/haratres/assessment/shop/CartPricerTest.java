package com.haratres.assessment.shop;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CartPricerTest {
    private final CartPricer pricer = new CartPricer();

    // --- assignment example ---

    @Test void assignmentExample_defterThenKalem() {
        // Defter 2×3.00, Kalem 2×1.50 → Defter discounted by Kalem price
        // Defter unit: 3.00-1.50=1.50, total: 2×1.50=3.00
        // Kalem unit: 1.50 (last entry, no discount), total: 2×1.50=3.00
        // Grand total: 6.00
        List<CartItem> items = List.of(
                cartItem("Defter", "3.00", 2),
                cartItem("Kalem", "1.50", 2));
        PricingResult result = pricer.calculate(items);

        assertEquals(2, result.lines().size());

        LineResult defter = result.lines().get(0);
        assertEquals("Defter", defter.productName());
        assertEquals(bd("1.50"), defter.discountedUnitPrice());
        assertEquals(bd("3.00"), defter.lineTotal());

        LineResult kalem = result.lines().get(1);
        assertEquals("Kalem", kalem.productName());
        assertEquals(bd("1.50"), kalem.discountedUnitPrice());
        assertEquals(bd("1.50"), kalem.originalUnitPrice());
        assertEquals(bd("3.00"), kalem.lineTotal());

        assertEquals(bd("6.00"), result.finalTotal());
    }

    // --- three-entry scenario ---

    @Test void threeEntries_10_6_2() {
        // Prices 10.00, 6.00, 2.00 qty 1 each
        // Entry 0: 10.00 > 6.00 → discounted to 4.00
        // Entry 1: 6.00 > 2.00 → discounted to 4.00
        // Entry 2: last → 2.00
        // Total: 4+4+2 = 10.00
        List<CartItem> items = List.of(
                cartItem("A", "10.00", 1),
                cartItem("B", "6.00", 1),
                cartItem("C", "2.00", 1));
        PricingResult result = pricer.calculate(items);

        assertEquals(bd("4.00"), result.lines().get(0).discountedUnitPrice());
        assertEquals(bd("4.00"), result.lines().get(1).discountedUnitPrice());
        assertEquals(bd("2.00"), result.lines().get(2).discountedUnitPrice());
        assertEquals(bd("10.00"), result.finalTotal());
    }

    // --- equal prices: no discount ---

    @Test void equalPricesProduceNoDiscount() {
        List<CartItem> items = List.of(
                cartItem("A", "5.00", 1),
                cartItem("B", "5.00", 1));
        PricingResult result = pricer.calculate(items);
        assertEquals(bd("5.00"), result.lines().get(0).discountedUnitPrice());
        assertEquals(bd("10.00"), result.finalTotal());
    }

    // --- increasing prices: no discount ---

    @Test void increasingPricesProduceNoDiscount() {
        List<CartItem> items = List.of(
                cartItem("A", "1.00", 2),
                cartItem("B", "5.00", 1));
        PricingResult result = pricer.calculate(items);
        assertEquals(bd("1.00"), result.lines().get(0).discountedUnitPrice());
        assertEquals(bd("7.00"), result.finalTotal());
    }

    // --- single entry (edge case) ---

    @Test void singleEntryHasNoDiscount() {
        List<CartItem> items = List.of(cartItem("A", "5.00", 3));
        PricingResult result = pricer.calculate(items);
        assertEquals(bd("5.00"), result.lines().get(0).discountedUnitPrice());
        assertEquals(bd("15.00"), result.finalTotal());
    }

    // --- original prices are not mutated ---

    @Test void originalUnitPricesPreservedAfterDiscount() {
        List<CartItem> items = List.of(
                cartItem("A", "10.00", 1),
                cartItem("B", "3.00", 1));
        PricingResult r1 = pricer.calculate(items);
        PricingResult r2 = pricer.calculate(items);
        assertEquals(r1.finalTotal(), r2.finalTotal());
        assertEquals(bd("10.00"), r1.lines().get(0).originalUnitPrice());
        assertEquals(bd("10.00"), r2.lines().get(0).originalUnitPrice());
    }

    // --- rejects null/empty ---

    @Test void rejectsNullOrEmptyCartItems() {
        assertThrows(IllegalArgumentException.class, () -> pricer.calculate(null));
        assertThrows(IllegalArgumentException.class, () -> pricer.calculate(List.of()));
    }

    // --- helpers ---

    private static CartItem cartItem(String name, String price, int qty) {
        Product p = new Product(name, bd(price), qty + 10 /* ample stock */, bd("3.0"));
        return new CartItem(p, qty);
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
