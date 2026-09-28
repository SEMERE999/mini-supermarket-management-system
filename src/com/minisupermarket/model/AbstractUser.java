package com.minisupermarket.model;

import com.minisupermarket.util.PasswordUtil;

public abstract class AbstractUser implements Authenticatable {
    private final int userId;
    private final String fullName;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean active;

    protected AbstractUser(int userId, String fullName, String username, String passwordHash, Role role, boolean active) {
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean matchesPassword(String plainTextPassword) {
        return passwordHash.equals(PasswordUtil.hash(plainTextPassword));
    }

    public abstract String permissionsSummary();

    @Override
    public String toString() {
        return String.format("%d | %s | %s | %s", userId, fullName, username, role);
    }
}
