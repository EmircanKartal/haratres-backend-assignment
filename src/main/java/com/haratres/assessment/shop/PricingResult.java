package com.haratres.assessment.shop;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full pricing result for a checkout.
 *
 * <p>Immutable; repeated calls to {@link CartPricer#calculate} with the same
 * cart produce equal {@code PricingResult} instances.
 */
public record PricingResult(List<LineResult> lines, BigDecimal finalTotal) {
    public PricingResult {
        lines = List.copyOf(lines);
    }
}
