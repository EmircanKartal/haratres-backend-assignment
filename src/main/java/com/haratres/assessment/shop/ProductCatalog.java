package com.haratres.assessment.shop;

import com.haratres.assessment.charactercount.CharacterCounter;
import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Holds the product list in the order products were added.
 *
 * <p>Names are compared using Turkish case-insensitive folding (the same
 * {@code fold} helper used by the character counter). Duplicate names are
 * rejected. Sorting returns a new list and never mutates the catalog.
 */
public final class ProductCatalog {
    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final int MAX_NAME_CODE_POINTS = 20;

    private final List<Product> products = new ArrayList<>();

    /**
     * Adds a product to the catalog.
     *
     * @throws IllegalArgumentException if the name is too long or a duplicate exists
     */
    public void add(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Ürün null olamaz.");
        }
        int nameLength = CharacterCounter.codePointLength(product.displayName());
        if (nameLength > MAX_NAME_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "Ürün adı en fazla " + MAX_NAME_CODE_POINTS + " karakter olabilir.");
        }
        String folded = foldName(product.displayName());
        boolean duplicate = products.stream()
                .anyMatch(p -> foldName(p.displayName()).equals(folded));
        if (duplicate) {
            throw new IllegalArgumentException(
                    "'" + product.displayName() + "' adında bir ürün zaten mevcut.");
        }
        products.add(product);
    }

    /** Returns an unmodifiable view of products in insertion order. */
    public List<Product> products() {
        return List.copyOf(products);
    }

    /** Returns the number of products in the catalog. */
    public int size() {
        return products.size();
    }

    /**
     * Returns a new sorted list; does not mutate the catalog.
     *
     * <p>Ties for STOCK and RATING use Turkish name order as a tiebreaker.
     * Ties for NAME use the numeric code-point value of the first differing character.
     */
    public List<Product> sortedBy(SortField field, SortOrder order) {
        Collator turkishOrder = Collator.getInstance(TURKISH);
        turkishOrder.setStrength(Collator.IDENTICAL);

        Comparator<Product> nameComp = Comparator.comparing(Product::displayName, turkishOrder);

        Comparator<Product> comparator = switch (field) {
            case NAME -> nameComp;
            case STOCK -> Comparator.comparingInt(Product::stock).thenComparing(nameComp);
            case RATING -> Comparator.comparing(Product::rating, BigDecimal::compareTo)
                    .thenComparing(nameComp);
        };

        if (order == SortOrder.DESC) {
            comparator = comparator.reversed();
        }

        List<Product> sorted = new ArrayList<>(products);
        sorted.sort(comparator);
        return List.copyOf(sorted);
    }

    /**
     * Looks up a product by name using Turkish case-insensitive folding.
     */
    public Optional<Product> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String folded = foldName(name);
        return products.stream()
                .filter(p -> foldName(p.displayName()).equals(folded))
                .findFirst();
    }

    /** Turkish case-insensitive key for a product name. */
    static String foldName(String name) {
        StringBuilder sb = new StringBuilder();
        name.codePoints().map(CharacterCounter::fold).forEach(cp -> sb.appendCodePoint(cp));
        return sb.toString();
    }
}
