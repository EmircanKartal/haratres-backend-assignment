package com.haratres.assessment.shop;

/**
 * One line in the shopping cart: a product and the cumulative quantity requested.
 * Mutable quantity so that repeated additions to the same product can be merged.
 */
public final class CartItem {
    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("Ürün null olamaz.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Adet en az 1 olmalıdır.");
        }
        this.product = product;
        this.quantity = quantity;
    }

    public Product product() {
        return product;
    }

    public int quantity() {
        return quantity;
    }

    /** Merges an additional quantity into this item. */
    void addQuantity(int extra) {
        if (extra < 1) {
            throw new IllegalArgumentException("Eklenecek adet en az 1 olmalıdır.");
        }
        this.quantity += extra;
    }
}
