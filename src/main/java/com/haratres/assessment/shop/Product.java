package com.haratres.assessment.shop;

import java.math.BigDecimal;

/**
 * Immutable product snapshot.
 *
 * <p>{@code displayName} is the readable name as the user typed it.
 * {@code price} is the unit price; always constructed from a String to avoid
 * floating-point representation errors.
 * {@code stock} is the initial available quantity.
 * {@code rating} is on a 0–5 scale (decimals allowed; lower bound is assumed).
 */
public record Product(String displayName, BigDecimal price, int stock, BigDecimal rating) {
    public Product {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Ürün adı boş olamaz.");
        }
        if (price == null || price.compareTo(BigDecimal.ONE) < 0
                || price.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Birim fiyat 1 ile 100 arasında olmalıdır.");
        }
        if (stock < 1) {
            throw new IllegalArgumentException("Stok miktarı en az 1 olmalıdır.");
        }
        if (rating == null || rating.compareTo(BigDecimal.ZERO) < 0
                || rating.compareTo(new BigDecimal("5")) > 0) {
            throw new IllegalArgumentException("Değerlendirme puanı 0 ile 5 arasında olmalıdır.");
        }
    }
}
