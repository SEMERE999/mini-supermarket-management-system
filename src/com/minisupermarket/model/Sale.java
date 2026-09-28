package com.minisupermarket.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Sale implements Billable {
    private final int saleId;
    private final LocalDateTime saleDate;
    private final Customer customer;
    private final AbstractUser cashier;
    private final BigDecimal paymentAmount;
    private final SaleStatus status;
    private final List<SaleItem> items;

    public Sale(int saleId, LocalDateTime saleDate, Customer customer, AbstractUser cashier,
                BigDecimal paymentAmount, SaleStatus status, List<SaleItem> items) {
        this.saleId = saleId;
        this.saleDate = saleDate;
        this.customer = customer;
        this.cashier = cashier;
        this.paymentAmount = paymentAmount;
        this.status = status;
        this.items = new ArrayList<>(items);
    }

    public int getSaleId() {
        return saleId;
    }

    public LocalDateTime getSaleDate() {
        return saleDate;
    }

    public Customer getCustomer() {
        return customer;
    }

    public AbstractUser getCashier() {
        return cashier;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    @Override
    public BigDecimal calculateTotal() {
        return items.stream()
            .map(SaleItem::calculateTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateChange() {
        return paymentAmount.subtract(calculateTotal());
    }
}
