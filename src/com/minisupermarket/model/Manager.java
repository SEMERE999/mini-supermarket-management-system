package com.minisupermarket.model;

public class Manager extends AbstractUser {
    public Manager(int userId, String fullName, String username, String passwordHash, boolean active) {
        super(userId, fullName, username, passwordHash, Role.MANAGER, active);
    }

    @Override
    public String permissionsSummary() {
        return "Can manage users, products, categories, stock, and sales reports.";
    }
}
