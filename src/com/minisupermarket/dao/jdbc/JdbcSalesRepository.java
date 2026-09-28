package com.minisupermarket.dao.jdbc;

import com.minisupermarket.config.ConnectionFactory;
import com.minisupermarket.dao.SalesRepository;
import com.minisupermarket.model.AbstractUser;
import com.minisupermarket.model.Cashier;
import com.minisupermarket.model.Category;
import com.minisupermarket.model.Customer;
import com.minisupermarket.model.DailySalesSummary;
import com.minisupermarket.model.Product;
import com.minisupermarket.model.Sale;
import com.minisupermarket.model.SaleItem;
import com.minisupermarket.model.SaleStatus;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcSalesRepository implements SalesRepository {
    @Override
    public Customer saveCustomer(Customer customer) throws SQLException {
        String sql = """
            INSERT INTO dbo.Customer (FullName, Phone)
            OUTPUT INSERTED.CustomerId
            VALUES (?, ?)
            """;
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, customer.getFullName());
            statement.setString(2, customer.getPhone());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new Customer(resultSet.getInt(1), customer.getFullName(), customer.getPhone());
            }
        }
    }

    @Override
    public Optional<Customer> findCustomerByPhone(String phone) throws SQLException {
        String sql = "SELECT CustomerId, FullName, Phone FROM dbo.Customer WHERE Phone = ?";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, phone);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapCustomer(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Customer> findAllCustomers() throws SQLException {
        String sql = "SELECT CustomerId, FullName, Phone FROM dbo.Customer ORDER BY FullName";
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                customers.add(mapCustomer(resultSet));
            }
        }
        return customers;
    }

    @Override
    public int createSale(Integer customerId, int cashierId, BigDecimal paymentAmount) throws SQLException {
        String sql = "{call dbo.sp_CreateSale(?, ?, ?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             CallableStatement statement = connection.prepareCall(sql)) {
            if (customerId == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, customerId);
            }
            statement.setInt(2, cashierId);
            statement.setBigDecimal(3, paymentAmount);
            statement.registerOutParameter(4, Types.INTEGER);
            statement.execute();
            return statement.getInt(4);
        }
    }

    @Override
    public void addSaleItem(int saleId, String productCode, int quantity) throws SQLException {
        String sql = "{call dbo.sp_AddSaleItem(?, ?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, saleId);
            statement.setString(2, productCode);
            statement.setInt(3, quantity);
            statement.execute();
        }
    }

    @Override
    public void finalizeSale(int saleId, BigDecimal paymentAmount) throws SQLException {
        String sql = "{call dbo.sp_FinalizeSale(?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, saleId);
            statement.setBigDecimal(2, paymentAmount);
            statement.execute();
        }
    }

    @Override
    public void cancelSale(int saleId) throws SQLException {
        String sql = "{call dbo.sp_CancelSale(?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, saleId);
            statement.execute();
        }
    }

    @Override
    public List<Sale> findCompletedSalesByCashier(int cashierId) throws SQLException {
        String sql = baseSalesQuery() + " WHERE s.Status = 'COMPLETED' AND s.CashierId = ? ORDER BY s.SaleDate DESC, s.SaleId DESC";
        return querySales(sql, cashierId, true);
    }

    @Override
    public List<Sale> findAllCompletedSales() throws SQLException {
        String sql = baseSalesQuery() + " WHERE s.Status = 'COMPLETED' ORDER BY s.SaleDate DESC, s.SaleId DESC";
        return querySales(sql, null, false);
    }

    @Override
    public List<DailySalesSummary> findDailySalesSummary(LocalDate salesDate) throws SQLException {
        String sql = """
            SELECT SalesDate, CashierName, SalesCount, TotalRevenue, ItemsSold
            FROM dbo.vw_DailySalesSummary
            WHERE SalesDate = ?
            ORDER BY CashierName
            """;
        List<DailySalesSummary> rows = new ArrayList<>();
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, java.sql.Date.valueOf(salesDate));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new DailySalesSummary(
                        resultSet.getDate("SalesDate").toLocalDate(),
                        resultSet.getString("CashierName"),
                        resultSet.getInt("SalesCount"),
                        resultSet.getBigDecimal("TotalRevenue"),
                        resultSet.getInt("ItemsSold")
                    ));
                }
            }
        }
        return rows;
    }

    private String baseSalesQuery() {
        return """
            SELECT
                s.SaleId, s.SaleDate, s.PaymentAmount, s.Status,
                c.CustomerId, c.FullName AS CustomerName, c.Phone,
                u.UserId, u.FullName AS CashierName, u.Username, u.PasswordHash, u.IsActive,
                si.SaleItemId, si.Quantity, si.UnitPrice,
                p.ProductCode, p.ProductName, p.StockQuantity,
                cat.CategoryId, cat.CategoryName
            FROM dbo.Sale AS s
            LEFT JOIN dbo.Customer AS c ON c.CustomerId = s.CustomerId
            INNER JOIN dbo.AppUser AS u ON u.UserId = s.CashierId
            INNER JOIN dbo.SaleItem AS si ON si.SaleId = s.SaleId
            INNER JOIN dbo.Product AS p ON p.ProductCode = si.ProductCode
            INNER JOIN dbo.Category AS cat ON cat.CategoryId = p.CategoryId
            """;
    }

    private List<Sale> querySales(String sql, Integer cashierId, boolean filterByCashier) throws SQLException {
        Map<Integer, SaleAccumulator> sales = new LinkedHashMap<>();
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (filterByCashier && cashierId != null) {
                statement.setInt(1, cashierId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int saleId = resultSet.getInt("SaleId");
                    SaleAccumulator accumulator = sales.computeIfAbsent(saleId, key -> buildAccumulator(resultSet));
                    accumulator.items.add(buildSaleItem(resultSet));
                }
            }
        }

        List<Sale> mappedSales = new ArrayList<>();
        for (SaleAccumulator accumulator : sales.values()) {
            mappedSales.add(accumulator.toSale());
        }
        return mappedSales;
    }

    private SaleAccumulator buildAccumulator(ResultSet resultSet) {
        try {
            Customer customer = null;
            int customerId = resultSet.getInt("CustomerId");
            if (!resultSet.wasNull()) {
                customer = new Customer(
                    customerId,
                    resultSet.getString("CustomerName"),
                    resultSet.getString("Phone")
                );
            }

            AbstractUser cashier = new Cashier(
                resultSet.getInt("UserId"),
                resultSet.getString("CashierName"),
                resultSet.getString("Username"),
                resultSet.getString("PasswordHash"),
                resultSet.getBoolean("IsActive")
            );

            return new SaleAccumulator(
                resultSet.getInt("SaleId"),
                resultSet.getTimestamp("SaleDate").toLocalDateTime(),
                customer,
                cashier,
                resultSet.getBigDecimal("PaymentAmount"),
                SaleStatus.fromDatabaseValue(resultSet.getString("Status"))
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to map sale header.", ex);
        }
    }

    private SaleItem buildSaleItem(ResultSet resultSet) throws SQLException {
        Category category = new Category(
            resultSet.getInt("CategoryId"),
            resultSet.getString("CategoryName")
        );
        Product product = new Product(
            resultSet.getString("ProductCode"),
            resultSet.getString("ProductName"),
            resultSet.getBigDecimal("UnitPrice"),
            resultSet.getInt("StockQuantity"),
            category
        );
        return new SaleItem(
            resultSet.getInt("SaleItemId"),
            product,
            resultSet.getInt("Quantity"),
            resultSet.getBigDecimal("UnitPrice")
        );
    }

    private Customer mapCustomer(ResultSet resultSet) throws SQLException {
        return new Customer(
            resultSet.getInt("CustomerId"),
            resultSet.getString("FullName"),
            resultSet.getString("Phone")
        );
    }

    private static class SaleAccumulator {
        private final int saleId;
        private final LocalDateTime saleDate;
        private final Customer customer;
        private final AbstractUser cashier;
        private final BigDecimal paymentAmount;
        private final SaleStatus status;
        private final List<SaleItem> items = new ArrayList<>();

        private SaleAccumulator(int saleId, LocalDateTime saleDate, Customer customer,
                                AbstractUser cashier, BigDecimal paymentAmount, SaleStatus status) {
            this.saleId = saleId;
            this.saleDate = saleDate;
            this.customer = customer;
            this.cashier = cashier;
            this.paymentAmount = paymentAmount;
            this.status = status;
        }

        private Sale toSale() {
            return new Sale(saleId, saleDate, customer, cashier, paymentAmount, status, items);
        }
    }
}
