package com.haratres.assessment.shop;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductCatalogTest {
    private static final Product KALEM = product("Kalem", "1.50", 100, "4.5");
    private static final Product DEFTER = product("Defter", "3.00", 50, "4.7");
    private static final Product SILGI = product("Silgi", "1.00", 200, "4.0");

    // --- add constraints ---

    @Test void rejectsProductWithBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("  ", bd("1.00"), 1, bd("3.0")));
    }

    @Test void rejectsPriceOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("Test", bd("0.99"), 1, bd("3.0")));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("Test", bd("100.01"), 1, bd("3.0")));
    }

    @Test void rejectsStockLessThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("Test", bd("5.00"), 0, bd("3.0")));
    }

    @Test void rejectsRatingOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("Test", bd("5.00"), 1, bd("-0.01")));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("Test", bd("5.00"), 1, bd("5.01")));
    }

    @Test void rejectsNameLongerThan20CodePoints() {
        String longName = "A".repeat(21);
        ProductCatalog catalog = new ProductCatalog();
        assertThrows(IllegalArgumentException.class,
                () -> catalog.add(new Product(longName, bd("5.00"), 1, bd("3.0"))));
    }

    @Test void acceptsNameOfExactly20CodePoints() {
        String name = "A".repeat(20);
        ProductCatalog catalog = new ProductCatalog();
        assertDoesNotThrow(() -> catalog.add(new Product(name, bd("5.00"), 1, bd("3.0"))));
    }

    @Test void rejectsDuplicateNameCaseInsensitive() {
        ProductCatalog catalog = new ProductCatalog();
        catalog.add(KALEM);
        assertThrows(IllegalArgumentException.class,
                () -> catalog.add(product("kalem", "2.00", 10, "3.0")));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.add(product("KALEM", "2.00", 10, "3.0")));
    }

    @Test void rejectsDuplicateNameWithTurkishFolding() {
        // "İstanbul" and "istanbul" should be treated as the same name
        ProductCatalog catalog = new ProductCatalog();
        catalog.add(product("İstanbul", "5.00", 10, "3.0"));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.add(product("istanbul", "5.00", 10, "3.0")));
    }

    @Test void preservesInsertionOrder() {
        ProductCatalog catalog = catalogWith(KALEM, DEFTER, SILGI);
        assertEquals(List.of(KALEM, DEFTER, SILGI), catalog.products());
    }

    // --- findByName ---

    @Test void findsByNameCaseInsensitive() {
        ProductCatalog catalog = catalogWith(KALEM);
        assertTrue(catalog.findByName("kalem").isPresent());
        assertTrue(catalog.findByName("KALEM").isPresent());
    }

    @Test void findReturnsEmptyForUnknownName() {
        ProductCatalog catalog = catalogWith(KALEM);
        assertTrue(catalog.findByName("Silgi").isEmpty());
        assertTrue(catalog.findByName(null).isEmpty());
    }

    // --- sortedBy ---

    @Test void sortByNameAscending() {
        ProductCatalog catalog = catalogWith(DEFTER, KALEM, SILGI);
        List<Product> sorted = catalog.sortedBy(SortField.NAME, SortOrder.ASC);
        // Turkish order: D, K, S
        assertEquals("Defter", sorted.get(0).displayName());
        assertEquals("Kalem", sorted.get(1).displayName());
        assertEquals("Silgi", sorted.get(2).displayName());
    }

    @Test void sortByNameDescending() {
        ProductCatalog catalog = catalogWith(DEFTER, KALEM, SILGI);
        List<Product> sorted = catalog.sortedBy(SortField.NAME, SortOrder.DESC);
        assertEquals("Silgi", sorted.get(0).displayName());
    }

    @Test void sortByRatingDescending() {
        ProductCatalog catalog = catalogWith(KALEM, DEFTER, SILGI);
        List<Product> sorted = catalog.sortedBy(SortField.RATING, SortOrder.DESC);
        assertEquals("Defter", sorted.get(0).displayName()); // 4.7
        assertEquals("Kalem", sorted.get(1).displayName());  // 4.5
        assertEquals("Silgi", sorted.get(2).displayName());  // 4.0
    }

    @Test void sortByStockAscending() {
        ProductCatalog catalog = catalogWith(KALEM, DEFTER, SILGI);
        List<Product> sorted = catalog.sortedBy(SortField.STOCK, SortOrder.ASC);
        assertEquals("Defter", sorted.get(0).displayName());  // 50
        assertEquals("Kalem", sorted.get(1).displayName());   // 100
        assertEquals("Silgi", sorted.get(2).displayName());   // 200
    }

    @Test void sortDoesNotMutateCatalogOrder() {
        ProductCatalog catalog = catalogWith(KALEM, DEFTER);
        catalog.sortedBy(SortField.NAME, SortOrder.DESC);
        assertEquals(List.of(KALEM, DEFTER), catalog.products());
    }

    // --- helpers ---

    private static ProductCatalog catalogWith(Product... products) {
        ProductCatalog catalog = new ProductCatalog();
        for (Product p : products) {
            catalog.add(p);
        }
        return catalog;
    }

    private static Product product(String name, String price, int stock, String rating) {
        return new Product(name, bd(price), stock, bd(rating));
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
