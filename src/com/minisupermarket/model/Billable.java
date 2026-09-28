package com.minisupermarket.model;

import java.math.BigDecimal;

public interface Billable {
    BigDecimal calculateTotal();
}
