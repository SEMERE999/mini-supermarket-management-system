package com.minisupermarket.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SaleRequest {
    private final Integer customerId;
    private final int cashierId;
    private final BigDecimal paymentAmount;
    private final List<SaleRequestItem> items;

    public SaleRequest(Integer customerId, int cashierId, BigDecimal paymentAmount, List<SaleRequestItem> items) {
        this.customerId = customerId;
        this.cashierId = cashierId;
        this.paymentAmount = paymentAmount;
        this.items = new ArrayList<>(items);
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public int getCashierId() {
        return cashierId;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public List<SaleRequestItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
