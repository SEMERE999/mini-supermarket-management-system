package com.minisupermarket.model;

public interface Authenticatable {
    boolean matchesPassword(String plainTextPassword);
}
