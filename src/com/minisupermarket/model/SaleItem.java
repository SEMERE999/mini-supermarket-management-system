package com.minisupermarket.model;

import java.math.BigDecimal;

public class SaleItem implements Billable {
    private final int saleItemId;
    private final Product product;
    private final int quantity;
    private final BigDecimal unitPrice;

    public SaleItem(int saleItemId, Product product, int quantity, BigDecimal unitPrice) {
        this.saleItemId = saleItemId;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int getSaleItemId() {
        return saleItemId;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    @Override
    public BigDecimal calculateTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
