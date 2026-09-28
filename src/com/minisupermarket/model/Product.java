package com.minisupermarket.model;

import java.math.BigDecimal;

public class Product {
    private final String productCode;
    private final String productName;
    private final BigDecimal unitPrice;
    private final int stockQuantity;
    private final Category category;

    public Product(String productCode, String productName, BigDecimal unitPrice, int stockQuantity, Category category) {
        this.productCode = productCode;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.stockQuantity = stockQuantity;
        this.category = category;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return String.format("%s | %s | %s | stock=%d | category=%s",
            productCode,
            productName,
            unitPrice,
            stockQuantity,
            category.getCategoryName());
    }
}
