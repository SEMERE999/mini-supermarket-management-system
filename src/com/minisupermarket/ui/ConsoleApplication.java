package com.minisupermarket.ui;

import com.minisupermarket.dao.CatalogRepository;
import com.minisupermarket.dao.SalesRepository;
import com.minisupermarket.dao.UserRepository;
import com.minisupermarket.dao.jdbc.JdbcCatalogRepository;
import com.minisupermarket.dao.jdbc.JdbcSalesRepository;
import com.minisupermarket.dao.jdbc.JdbcUserRepository;
import com.minisupermarket.model.AbstractUser;
import com.minisupermarket.model.Category;
import com.minisupermarket.model.Customer;
import com.minisupermarket.model.DailySalesSummary;
import com.minisupermarket.model.Manager;
import com.minisupermarket.model.Product;
import com.minisupermarket.model.Role;
import com.minisupermarket.model.Sale;
import com.minisupermarket.model.SaleItem;
import com.minisupermarket.model.SaleRequest;
import com.minisupermarket.model.SaleRequestItem;
import com.minisupermarket.service.AuthenticationService;
import com.minisupermarket.service.InventoryService;
import com.minisupermarket.service.ReportingService;
import com.minisupermarket.service.SalesService;
import com.minisupermarket.service.UserManagementService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ConsoleApplication {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InputReader inputReader = new InputReader();
    private final AuthenticationService authenticationService;
    private final UserManagementService userManagementService;
    private final InventoryService inventoryService;
    private final SalesService salesService;
    private final ReportingService reportingService;

    public ConsoleApplication() {
        UserRepository userRepository = new JdbcUserRepository();
        CatalogRepository catalogRepository = new JdbcCatalogRepository();
        SalesRepository salesRepository = new JdbcSalesRepository();
        this.authenticationService = new AuthenticationService(userRepository);
        this.userManagementService = new UserManagementService(userRepository);
        this.inventoryService = new InventoryService(catalogRepository);
        this.salesService = new SalesService(salesRepository);
        this.reportingService = new ReportingService(salesRepository);
    }

    public void run() {
        System.out.println("Mini Supermarket System");
        System.out.println("Default sample accounts:");
        System.out.println("manager1 / Manager@123");
        System.out.println("cashier1 / Cashier@123");
        System.out.println();

        while (true) {
            try {
                AbstractUser user = loginLoop();
                if (user == null) {
                    return;
                }
                System.out.println("Logged in as: " + user.getFullName() + " (" + user.getRole() + ")");
                System.out.println(user.permissionsSummary());

                if (user instanceof Manager) {
                    showManagerMenu(user);
                } else {
                    showCashierMenu(user);
                }
            } catch (SQLException ex) {
                System.out.println("Database error: " + ex.getMessage());
                System.out.println("Make sure SQL Server is running, the schema is loaded, and the JDBC driver jar is on the classpath.");
            } catch (RuntimeException ex) {
                System.out.println("Application error: " + ex.getMessage());
            }
        }
    }

    private AbstractUser loginLoop() throws SQLException {
        while (true) {
            String username = inputReader.promptString("Username (or type EXIT): ");
            if ("EXIT".equalsIgnoreCase(username)) {
                return null;
            }
            String password = inputReader.promptString("Password: ");
            AbstractUser user = authenticationService.login(username, password);
            if (user != null) {
                return user;
            }
            System.out.println("Invalid username or password.");
        }
    }

    private void showManagerMenu(AbstractUser user) throws SQLException {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("Manager Menu");
            System.out.println("1. List products");
            System.out.println("2. List low-stock products");
            System.out.println("3. Add category");
            System.out.println("4. Add product");
            System.out.println("5. Restock product");
            System.out.println("6. Register user");
            System.out.println("7. List users");
            System.out.println("8. View daily sales summary");
            System.out.println("9. View all completed sales");
            System.out.println("0. Logout");

            int choice = inputReader.promptInt("Choose an option: ");
            switch (choice) {
                case 1 -> printProducts(inventoryService.listProducts());
                case 2 -> printProducts(inventoryService.listLowStockProducts(10));
                case 3 -> addCategory();
                case 4 -> addProduct();
                case 5 -> restockProduct();
                case 6 -> registerUser();
                case 7 -> userManagementService.listUsers().forEach(System.out::println);
                case 8 -> viewDailySalesSummary();
                case 9 -> printSales(salesService.listAllCompletedSales());
                case 0 -> running = false;
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private void showCashierMenu(AbstractUser user) throws SQLException {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("Cashier Menu");
            System.out.println("1. List products");
            System.out.println("2. List customers");
            System.out.println("3. Register customer");
            System.out.println("4. Process sale");
            System.out.println("5. View my completed sales");
            System.out.println("0. Logout");

            int choice = inputReader.promptInt("Choose an option: ");
            switch (choice) {
                case 1 -> printProducts(inventoryService.listProducts());
                case 2 -> salesService.listCustomers().forEach(System.out::println);
                case 3 -> registerCustomer();
                case 4 -> processSale(user);
                case 5 -> printSales(salesService.listSalesForCashier(user.getUserId()));
                case 0 -> running = false;
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private void addCategory() throws SQLException {
        String categoryName = inputReader.promptString("Category name: ");
        inventoryService.addCategory(categoryName);
        System.out.println("Category created successfully.");
    }

    private void addProduct() throws SQLException {
        List<Category> categories = inventoryService.listCategories();
        categories.forEach(System.out::println);
        String productCode = inputReader.promptString("Product code: ");
        String productName = inputReader.promptString("Product name: ");
        BigDecimal unitPrice = inputReader.promptBigDecimal("Unit price: ");
        int stockQuantity = inputReader.promptInt("Opening stock quantity: ");
        int categoryId = inputReader.promptInt("Category ID: ");

        Category category = categories.stream()
            .filter(item -> item.getCategoryId() == categoryId)
            .findFirst()
            .orElse(null);

        if (category == null) {
            System.out.println("Invalid category id.");
            return;
        }

        inventoryService.addProduct(productCode, productName, unitPrice, stockQuantity, category);
        System.out.println("Product added successfully.");
    }

    private void restockProduct() throws SQLException {
        String productCode = inputReader.promptString("Product code: ");
        int quantity = inputReader.promptInt("Quantity to add: ");
        inventoryService.restockProduct(productCode, quantity);
        System.out.println("Product restocked successfully.");
    }

    private void registerUser() throws SQLException {
        String fullName = inputReader.promptString("Full name: ");
        String username = inputReader.promptString("Username: ");
        String password = inputReader.promptString("Password: ");
        Role role;
        try {
            role = Role.fromDatabaseValue(inputReader.promptString("Role (MANAGER/CASHIER): "));
        } catch (IllegalArgumentException ex) {
            System.out.println("Invalid role. Please enter MANAGER or CASHIER.");
            return;
        }
        userManagementService.registerUser(fullName, username, password, role);
        System.out.println("User registered successfully.");
    }

    private void registerCustomer() throws SQLException {
        Customer customer = registerCustomerInteractive();
        System.out.println("Customer ready: " + customer);
    }

    private Customer registerCustomerInteractive() throws SQLException {
        String fullName = inputReader.promptString("Customer name: ");
        String phone = inputReader.promptOptionalString("Phone (leave blank for walk-in): ");
        return salesService.registerCustomer(fullName, phone);
    }

    private void processSale(AbstractUser cashier) throws SQLException {
        Integer customerId = inputReader.promptOptionalInt("Customer ID (leave blank for none): ");
        List<SaleRequestItem> items = new ArrayList<>();

        while (true) {
            String productCode = inputReader.promptString("Product code (or DONE): ");
            if ("DONE".equalsIgnoreCase(productCode)) {
                break;
            }
            int quantity = inputReader.promptInt("Quantity: ");
            items.add(new SaleRequestItem(productCode, quantity));
        }

        if (items.isEmpty()) {
            System.out.println("Sale cancelled because no products were entered.");
            return;
        }

        BigDecimal paymentAmount = inputReader.promptBigDecimal("Payment amount: ");
        SaleRequest saleRequest = new SaleRequest(customerId, cashier.getUserId(), paymentAmount, items);
        int saleId = salesService.processSale(saleRequest);
        System.out.println("Sale completed successfully. Sale ID: " + saleId);
    }

    private void viewDailySalesSummary() throws SQLException {
        String rawDate = inputReader.promptString("Date (yyyy-mm-dd): ");
        LocalDate salesDate;
        try {
            salesDate = LocalDate.parse(rawDate);
        } catch (DateTimeParseException ex) {
            System.out.println("Invalid date format. Please use yyyy-mm-dd.");
            return;
        }
        List<DailySalesSummary> summaries = reportingService.getDailySalesSummary(salesDate);
        if (summaries.isEmpty()) {
            System.out.println("No completed sales found for " + salesDate + ".");
            return;
        }

        for (DailySalesSummary summary : summaries) {
            System.out.printf(
                "%s | cashier=%s | sales=%d | items=%d | revenue=%s%n",
                summary.getSalesDate(),
                summary.getCashierName(),
                summary.getSalesCount(),
                summary.getItemsSold(),
                summary.getTotalRevenue()
            );
        }
    }

    private void printProducts(List<Product> products) {
        if (products.isEmpty()) {
            System.out.println("No products found.");
            return;
        }
        products.forEach(System.out::println);
    }

    private void printSales(List<Sale> sales) {
        if (sales.isEmpty()) {
            System.out.println("No sales found.");
            return;
        }

        for (Sale sale : sales) {
            String customerName = sale.getCustomer() == null ? "Walk-in / not recorded" : sale.getCustomer().getFullName();
            System.out.printf(
                "Sale #%d | %s | cashier=%s | customer=%s | total=%s | payment=%s | change=%s%n",
                sale.getSaleId(),
                sale.getSaleDate().format(DATE_TIME_FORMATTER),
                sale.getCashier().getFullName(),
                customerName,
                sale.calculateTotal(),
                sale.getPaymentAmount(),
                sale.calculateChange()
            );
            for (SaleItem item : sale.getItems()) {
                System.out.printf(
                    "   - %s x%d @ %s = %s%n",
                    item.getProduct().getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.calculateTotal()
                );
            }
        }
    }
}
