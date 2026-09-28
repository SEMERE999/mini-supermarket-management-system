package com.minisupermarket.model;

public class Customer {
    private final int customerId;
    private final String fullName;
    private final String phone;

    public Customer(int customerId, String fullName, String phone) {
        this.customerId = customerId;
        this.fullName = fullName;
        this.phone = phone;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public String toString() {
        if (phone == null || phone.isBlank()) {
            return customerId + " - " + fullName;
        }
        return customerId + " - " + fullName + " (" + phone + ")";
    }
}
