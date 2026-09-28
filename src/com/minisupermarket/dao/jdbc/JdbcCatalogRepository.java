package com.minisupermarket.dao.jdbc;

import com.minisupermarket.config.ConnectionFactory;
import com.minisupermarket.dao.CatalogRepository;
import com.minisupermarket.model.Category;
import com.minisupermarket.model.Product;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcCatalogRepository implements CatalogRepository {
    @Override
    public List<Category> findAllCategories() throws SQLException {
        String sql = "SELECT CategoryId, CategoryName FROM dbo.Category ORDER BY CategoryName";
        List<Category> categories = new ArrayList<>();
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(mapCategory(resultSet));
            }
        }
        return categories;
    }

    @Override
    public List<Product> findAllProducts() throws SQLException {
        String sql = """
            SELECT p.ProductCode, p.ProductName, p.UnitPrice, p.StockQuantity,
                   c.CategoryId, c.CategoryName
            FROM dbo.Product AS p
            INNER JOIN dbo.Category AS c ON c.CategoryId = p.CategoryId
            WHERE p.IsActive = 1
            ORDER BY p.ProductName
            """;
        return queryProducts(sql, null, 0);
    }

    @Override
    public List<Product> findProductsBelowStock(int threshold) throws SQLException {
        String sql = """
            SELECT p.ProductCode, p.ProductName, p.UnitPrice, p.StockQuantity,
                   c.CategoryId, c.CategoryName
            FROM dbo.Product AS p
            INNER JOIN dbo.Category AS c ON c.CategoryId = p.CategoryId
            WHERE p.IsActive = 1 AND p.StockQuantity <= ?
            ORDER BY p.StockQuantity, p.ProductName
            """;
        return queryProducts(sql, threshold, 1);
    }

    @Override
    public Optional<Product> findProductByCode(String productCode) throws SQLException {
        String sql = """
            SELECT p.ProductCode, p.ProductName, p.UnitPrice, p.StockQuantity,
                   c.CategoryId, c.CategoryName
            FROM dbo.Product AS p
            INNER JOIN dbo.Category AS c ON c.CategoryId = p.CategoryId
            WHERE p.ProductCode = ? AND p.IsActive = 1
            """;
        List<Product> products = queryProducts(sql, productCode, 2);
        return products.isEmpty() ? Optional.empty() : Optional.of(products.get(0));
    }

    @Override
    public void saveCategory(String categoryName) throws SQLException {
        String sql = "{call dbo.sp_AddCategory(?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setString(1, categoryName);
            statement.execute();
        }
    }

    @Override
    public void saveProduct(Product product) throws SQLException {
        String sql = "{call dbo.sp_AddProduct(?, ?, ?, ?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setString(1, product.getProductCode());
            statement.setString(2, product.getProductName());
            statement.setBigDecimal(3, product.getUnitPrice());
            statement.setInt(4, product.getStockQuantity());
            statement.setInt(5, product.getCategory().getCategoryId());
            statement.execute();
        }
    }

    @Override
    public void restockProduct(String productCode, int quantity) throws SQLException {
        String sql = "{call dbo.sp_RestockProduct(?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setString(1, productCode);
            statement.setInt(2, quantity);
            statement.execute();
        }
    }

    private List<Product> queryProducts(String sql, Object parameter, int parameterMode) throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameterMode == 1) {
                statement.setInt(1, (Integer) parameter);
            } else if (parameterMode == 2) {
                statement.setString(1, (String) parameter);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
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
                    products.add(product);
                }
            }
        }
        return products;
    }

    private Category mapCategory(ResultSet resultSet) throws SQLException {
        return new Category(
            resultSet.getInt("CategoryId"),
            resultSet.getString("CategoryName")
        );
    }
}
