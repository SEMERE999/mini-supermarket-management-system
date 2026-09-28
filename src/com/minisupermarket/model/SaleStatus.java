package com.minisupermarket.model;

public enum SaleStatus {
    PENDING,
    COMPLETED,
    CANCELLED;

    public static SaleStatus fromDatabaseValue(String value) {
        return SaleStatus.valueOf(value.toUpperCase());
    }
}
