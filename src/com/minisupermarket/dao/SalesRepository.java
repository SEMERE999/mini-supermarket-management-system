package com.minisupermarket.dao;

import com.minisupermarket.model.Customer;
import com.minisupermarket.model.DailySalesSummary;
import com.minisupermarket.model.Sale;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesRepository {
    Customer saveCustomer(Customer customer) throws SQLException;
    Optional<Customer> findCustomerByPhone(String phone) throws SQLException;
    List<Customer> findAllCustomers() throws SQLException;
    int createSale(Integer customerId, int cashierId, BigDecimal paymentAmount) throws SQLException;
    void addSaleItem(int saleId, String productCode, int quantity) throws SQLException;
    void finalizeSale(int saleId, BigDecimal paymentAmount) throws SQLException;
    void cancelSale(int saleId) throws SQLException;
    List<Sale> findCompletedSalesByCashier(int cashierId) throws SQLException;
    List<Sale> findAllCompletedSales() throws SQLException;
    List<DailySalesSummary> findDailySalesSummary(LocalDate salesDate) throws SQLException;
}
