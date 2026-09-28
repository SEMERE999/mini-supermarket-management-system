package com.minisupermarket.dao;

import com.minisupermarket.model.Category;
import com.minisupermarket.model.Product;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface CatalogRepository {
    List<Category> findAllCategories() throws SQLException;
    List<Product> findAllProducts() throws SQLException;
    List<Product> findProductsBelowStock(int threshold) throws SQLException;
    Optional<Product> findProductByCode(String productCode) throws SQLException;
    void saveCategory(String categoryName) throws SQLException;
    void saveProduct(Product product) throws SQLException;
    void restockProduct(String productCode, int quantity) throws SQLException;
}
