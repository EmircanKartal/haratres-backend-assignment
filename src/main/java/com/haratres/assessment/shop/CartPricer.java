package com.haratres.assessment.shop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the adjacent-comparison discount rule to a list of cart items.
 *
 * <h2>Discount rule (assumption — not explicitly stated for carts with more than two entries)</h2>
 * <p>Compare adjacent entries in insertion order using their <em>original</em> unit prices.
 * If entry {@code i}'s unit price is greater than entry {@code i+1}'s unit price,
 * subtract entry {@code i+1}'s unit price from entry {@code i}'s unit price
 * to obtain the discounted unit price for entry {@code i}.
 * The last entry is always left unchanged.
 * Catalog prices are never mutated. Discounted prices do not cascade.
 *
 * <h2>Monetary arithmetic</h2>
 * <p>All arithmetic uses {@link BigDecimal}; rounding uses {@link RoundingMode#HALF_UP}
 * to two decimal places. Prices are never constructed from floating-point literals.
 */
public final class CartPricer {
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * Calculates the discounted total for {@code items}.
     *
     * @param items the cart items in insertion order; must not be null or empty
     */
    public PricingResult calculate(List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Sepet boş olamaz.");
        }

        List<LineResult> lines = new ArrayList<>(items.size());
        BigDecimal finalTotal = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        for (int i = 0; i < items.size(); i++) {
            CartItem current = items.get(i);
            BigDecimal originalUnitPrice = current.product().price()
                    .setScale(SCALE, ROUNDING);

            BigDecimal discountedUnitPrice;
            if (i < items.size() - 1) {
                BigDecimal nextUnitPrice = items.get(i + 1).product().price()
                        .setScale(SCALE, ROUNDING);
                if (originalUnitPrice.compareTo(nextUnitPrice) > 0) {
                    discountedUnitPrice = originalUnitPrice.subtract(nextUnitPrice);
                } else {
                    discountedUnitPrice = originalUnitPrice;
                }
            } else {
                // last entry: no discount applied
                discountedUnitPrice = originalUnitPrice;
            }

            BigDecimal qty = BigDecimal.valueOf(current.quantity());
            BigDecimal lineTotal = discountedUnitPrice.multiply(qty)
                    .setScale(SCALE, ROUNDING);
            finalTotal = finalTotal.add(lineTotal);

            lines.add(new LineResult(
                    current.product().displayName(),
                    current.quantity(),
                    originalUnitPrice,
                    discountedUnitPrice,
                    lineTotal));
        }

        return new PricingResult(lines, finalTotal);
    }
}
