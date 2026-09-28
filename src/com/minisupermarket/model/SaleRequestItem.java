package com.minisupermarket.model;

public class SaleRequestItem {
    private final String productCode;
    private final int quantity;

    public SaleRequestItem(String productCode, int quantity) {
        this.productCode = productCode;
        this.quantity = quantity;
    }

    public String getProductCode() {
        return productCode;
    }

    public int getQuantity() {
        return quantity;
    }
}
