package com.minisupermarket.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailySalesSummary {
    private final LocalDate salesDate;
    private final String cashierName;
    private final int salesCount;
    private final BigDecimal totalRevenue;
    private final int itemsSold;

    public DailySalesSummary(LocalDate salesDate, String cashierName, int salesCount, BigDecimal totalRevenue, int itemsSold) {
        this.salesDate = salesDate;
        this.cashierName = cashierName;
        this.salesCount = salesCount;
        this.totalRevenue = totalRevenue;
        this.itemsSold = itemsSold;
    }

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public String getCashierName() {
        return cashierName;
    }

    public int getSalesCount() {
        return salesCount;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public int getItemsSold() {
        return itemsSold;
    }
}
