package com.minisupermarket.service;

import com.minisupermarket.dao.SalesRepository;
import com.minisupermarket.model.DailySalesSummary;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class ReportingService {
    private final SalesRepository salesRepository;

    public ReportingService(SalesRepository salesRepository) {
        this.salesRepository = salesRepository;
    }

    public List<DailySalesSummary> getDailySalesSummary(LocalDate salesDate) throws SQLException {
        return salesRepository.findDailySalesSummary(salesDate);
    }
}
