package com.haratres.assessment.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Insertion-ordered shopping cart backed by a {@link LinkedHashMap}.
 *
 * <p>Products are identified by Turkish case-insensitive folded name.
 * Repeated additions for the same product merge quantities while preserving
 * the product's original cart position.
 *
 * <p>Stock is validated against the catalog at the time of addition.
 * A rejected addition leaves the cart and stock tracking unchanged.
 *
 * <p>The cart tracks how much stock is reserved but does not subtract from
 * the catalog's stock field; validation compares cumulative cart quantity
 * against the original catalog stock.
 */
public final class ShoppingCart {
    private final ProductCatalog catalog;
    // key: folded product name; value: cart item (mutable quantity); insertion order preserved
    private final Map<String, CartItem> items = new LinkedHashMap<>();

    public ShoppingCart(ProductCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Katalog null olamaz.");
        }
        this.catalog = catalog;
    }

    /**
     * Adds {@code quantity} units of the named product.
     *
     * @throws IllegalArgumentException if the product is not found, quantity ≤ 0,
     *                                  or cumulative quantity would exceed available stock
     */
    public void add(String name, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Adet en az 1 olmalıdır.");
        }
        Product product = catalog.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "'" + name + "' adında bir ürün bulunamadı."));

        String key = ProductCatalog.foldName(name);
        int existing = items.containsKey(key) ? items.get(key).quantity() : 0;
        int total = existing + quantity;
        if (total > product.stock()) {
            throw new IllegalArgumentException(
                    "Stokta yeterli ürün yok. Mevcut stok: " + product.stock()
                            + ", sepetteki miktar: " + existing
                            + ", talep edilen ek miktar: " + quantity + ".");
        }

        if (items.containsKey(key)) {
            items.get(key).addQuantity(quantity);
        } else {
            items.put(key, new CartItem(product, quantity));
        }
    }

    /** Returns cart items in insertion order. */
    public List<CartItem> items() {
        return List.copyOf(items.values());
    }

    /** Returns the number of distinct products in the cart. */
    public int distinctProductCount() {
        return items.size();
    }

    /** Total number of individual units across all cart lines. */
    public int totalItemCount() {
        return items.values().stream().mapToInt(CartItem::quantity).sum();
    }
}
