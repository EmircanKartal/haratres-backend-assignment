package com.haratres.assessment.shop;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingCartTest {
    private ProductCatalog catalog;
    private ShoppingCart cart;

    @BeforeEach void setUp() {
        catalog = new ProductCatalog();
        catalog.add(new Product("Defter", bd("3.00"), 5, bd("4.7")));
        catalog.add(new Product("Kalem", bd("1.50"), 10, bd("4.5")));
        cart = new ShoppingCart(catalog);
    }

    @Test void addsProductByExactName() {
        cart.add("Defter", 2);
        assertEquals(1, cart.distinctProductCount());
        assertEquals(2, cart.items().get(0).quantity());
    }

    @Test void addsProductByCaseInsensitiveName() {
        cart.add("defter", 1);
        cart.add("KALEM", 2);
        assertEquals(2, cart.distinctProductCount());
    }

    @Test void mergesRepeatedAdditionsAndPreservesOrder() {
        cart.add("Defter", 2);
        cart.add("Kalem", 3);
        cart.add("Defter", 1); // merge; Defter stays first
        List<CartItem> items = cart.items();
        assertEquals(2, items.size());
        assertEquals("Defter", items.get(0).product().displayName());
        assertEquals(3, items.get(0).quantity()); // 2 + 1
        assertEquals("Kalem", items.get(1).product().displayName());
    }

    @Test void rejectsUnknownProduct() {
        assertThrows(IllegalArgumentException.class, () -> cart.add("Silgi", 1));
    }

    @Test void rejectsZeroOrNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> cart.add("Defter", 0));
        assertThrows(IllegalArgumentException.class, () -> cart.add("Defter", -1));
    }

    @Test void rejectsQuantityExceedingStock() {
        assertThrows(IllegalArgumentException.class, () -> cart.add("Defter", 6)); // stock is 5
    }

    @Test void rejectsCumulativeQuantityExceedingStock() {
        cart.add("Defter", 4);
        assertThrows(IllegalArgumentException.class, () -> cart.add("Defter", 2)); // 4+2=6 > 5
        // cart state unchanged: still 4
        assertEquals(4, cart.items().get(0).quantity());
    }

    @Test void rejectedAddDoesNotPartiallyMutateCart() {
        cart.add("Kalem", 9);
        int before = cart.items().stream()
                .filter(i -> i.product().displayName().equals("Kalem"))
                .findFirst().orElseThrow().quantity();
        assertThrows(IllegalArgumentException.class, () -> cart.add("Kalem", 2));
        int after = cart.items().stream()
                .filter(i -> i.product().displayName().equals("Kalem"))
                .findFirst().orElseThrow().quantity();
        assertEquals(before, after);
    }

    @Test void totalItemCountSumsAllQuantities() {
        cart.add("Defter", 2);
        cart.add("Kalem", 3);
        assertEquals(5, cart.totalItemCount());
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
