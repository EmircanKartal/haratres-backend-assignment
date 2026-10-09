package com.haratres.assessment.shop;

import java.math.BigDecimal;

/**
 * Pricing result for a single cart line.
 *
 * <p>{@code discountedUnitPrice} is the unit price after the adjacent-comparison
 * discount is applied. For the last cart entry and entries where no discount
 * applies it equals the original unit price.
 * {@code lineTotal} = {@code discountedUnitPrice} × {@code quantity}.
 */
public record LineResult(
        String productName,
        int quantity,
        BigDecimal originalUnitPrice,
        BigDecimal discountedUnitPrice,
        BigDecimal lineTotal
) { }
