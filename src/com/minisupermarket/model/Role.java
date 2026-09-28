package com.minisupermarket.model;

public enum Role {
    MANAGER,
    CASHIER;

    public static Role fromDatabaseValue(String value) {
        return Role.valueOf(value.toUpperCase());
    }
}
