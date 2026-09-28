package com.minisupermarket.service;

import com.minisupermarket.dao.CatalogRepository;
import com.minisupermarket.model.Category;
import com.minisupermarket.model.Product;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class InventoryService {
    private final CatalogRepository catalogRepository;

    public InventoryService(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public List<Category> listCategories() throws SQLException {
        return catalogRepository.findAllCategories();
    }

    public List<Product> listProducts() throws SQLException {
        return catalogRepository.findAllProducts();
    }

    public List<Product> listLowStockProducts(int threshold) throws SQLException {
        return catalogRepository.findProductsBelowStock(threshold);
    }

    public Optional<Product> findProduct(String productCode) throws SQLException {
        return catalogRepository.findProductByCode(productCode);
    }

    public void addCategory(String categoryName) throws SQLException {
        catalogRepository.saveCategory(categoryName);
    }

    public void addProduct(String productCode, String productName, BigDecimal unitPrice,
                           int stockQuantity, Category category) throws SQLException {
        Product product = new Product(productCode, productName, unitPrice, stockQuantity, category);
        catalogRepository.saveProduct(product);
    }

    public void restockProduct(String productCode, int quantity) throws SQLException {
        catalogRepository.restockProduct(productCode, quantity);
    }
}
