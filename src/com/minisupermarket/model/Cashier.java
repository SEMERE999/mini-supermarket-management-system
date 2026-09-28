package com.minisupermarket.model;

public class Cashier extends AbstractUser {
    public Cashier(int userId, String fullName, String username, String passwordHash, boolean active) {
        super(userId, fullName, username, passwordHash, Role.CASHIER, active);
    }

    @Override
    public String permissionsSummary() {
        return "Can process sales, register customers, and view assigned receipts.";
    }
}
