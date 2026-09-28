package com.minisupermarket.service;

import com.minisupermarket.dao.SalesRepository;
import com.minisupermarket.model.Customer;
import com.minisupermarket.model.Sale;
import com.minisupermarket.model.SaleRequest;
import com.minisupermarket.model.SaleRequestItem;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SalesService {
    private final SalesRepository salesRepository;

    public SalesService(SalesRepository salesRepository) {
        this.salesRepository = salesRepository;
    }

    public Customer registerCustomer(String fullName, String phone) throws SQLException {
        Optional<Customer> existingCustomer = phone == null || phone.isBlank()
            ? Optional.empty()
            : salesRepository.findCustomerByPhone(phone);

        if (existingCustomer.isPresent()) {
            return existingCustomer.get();
        }

        return salesRepository.saveCustomer(new Customer(0, fullName, phone));
    }

    public List<Customer> listCustomers() throws SQLException {
        return salesRepository.findAllCustomers();
    }

    public int processSale(SaleRequest saleRequest) throws SQLException {
        int saleId = salesRepository.createSale(
            saleRequest.getCustomerId(),
            saleRequest.getCashierId(),
            saleRequest.getPaymentAmount()
        );

        try {
            for (SaleRequestItem item : saleRequest.getItems()) {
                salesRepository.addSaleItem(saleId, item.getProductCode(), item.getQuantity());
            }
            salesRepository.finalizeSale(saleId, saleRequest.getPaymentAmount());
            return saleId;
        } catch (SQLException ex) {
            salesRepository.cancelSale(saleId);
            throw ex;
        }
    }

    public List<Sale> listSalesForCashier(int cashierId) throws SQLException {
        return salesRepository.findCompletedSalesByCashier(cashierId);
    }

    public List<Sale> listAllCompletedSales() throws SQLException {
        return salesRepository.findAllCompletedSales();
    }
}
