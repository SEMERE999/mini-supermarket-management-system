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
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

public class DesktopApplication {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuthenticationService authenticationService;
    private final UserManagementService userManagementService;
    private final InventoryService inventoryService;
    private final SalesService salesService;
    private final ReportingService reportingService;

    private final JFrame frame;
    private final CardLayout rootLayout;
    private final JPanel rootPanel;
    private final JLabel loginStatusLabel;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPanel dashboardContainer;

    private AbstractUser currentUser;

    public DesktopApplication() {
        UserRepository userRepository = new JdbcUserRepository();
        CatalogRepository catalogRepository = new JdbcCatalogRepository();
        SalesRepository salesRepository = new JdbcSalesRepository();
        this.authenticationService = new AuthenticationService(userRepository);
        this.userManagementService = new UserManagementService(userRepository);
        this.inventoryService = new InventoryService(catalogRepository);
        this.salesService = new SalesService(salesRepository);
        this.reportingService = new ReportingService(salesRepository);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        frame = new JFrame("Mini Supermarket System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 720));
        frame.setLocationByPlatform(true);

        rootLayout = new CardLayout();
        rootPanel = new JPanel(rootLayout);

        usernameField = new JTextField(18);
        passwordField = new JPasswordField(18);
        loginStatusLabel = new JLabel("Use manager1 / Manager@123 or cashier1 / Cashier@123");
        loginStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dashboardContainer = new JPanel(new BorderLayout());

        rootPanel.add(buildLoginPanel(), "LOGIN");
        rootPanel.add(dashboardContainer, "DASHBOARD");
        frame.setContentPane(rootPanel);
    }

    public void show() {
        frame.pack();
        frame.setVisible(true);
        rootLayout.show(rootPanel, "LOGIN");
    }

    private JPanel buildLoginPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(24, 24, 24, 24),
            BorderFactory.createTitledBorder("Login")
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets.set(8, 8, 8, 8);

        JLabel title = new JLabel("Mini Supermarket System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        card.add(title, gbc);

        gbc.gridy++;
        JLabel subtitle = new JLabel("Desktop version with manager and cashier dashboards");
        card.add(subtitle, gbc);

        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy++;
        card.add(new JLabel("Username"), gbc);
        gbc.gridx = 1;
        card.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy++;
        card.add(new JLabel("Password"), gbc);
        gbc.gridx = 1;
        card.add(passwordField, gbc);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(event -> doLogin());
        passwordField.addActionListener(event -> doLogin());

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        card.add(loginButton, gbc);

        gbc.gridy++;
        card.add(loginStatusLabel, gbc);

        outer.add(card);
        return outer;
    }

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isBlank() || password.isBlank()) {
            loginStatusLabel.setText("Please enter both username and password.");
            return;
        }

        try {
            AbstractUser user = authenticationService.login(username, password);
            if (user == null) {
                loginStatusLabel.setText("Invalid username or password.");
                return;
            }

            currentUser = user;
            passwordField.setText("");
            usernameField.setText("");
            loginStatusLabel.setText("Use manager1 / Manager@123 or cashier1 / Cashier@123");
            buildDashboardFor(user);
            rootLayout.show(rootPanel, "DASHBOARD");
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void buildDashboardFor(AbstractUser user) {
        dashboardContainer.removeAll();

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel welcome = new JLabel("Welcome, " + user.getFullName() + " (" + user.getRole() + ")");
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel(user.permissionsSummary());
        subtitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel leftHeader = new JPanel();
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.Y_AXIS));
        leftHeader.add(welcome);
        leftHeader.add(subtitle);

        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(event -> logout());

        header.add(leftHeader, BorderLayout.WEST);
        header.add(logoutButton, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Products", createProductsPanel());

        if (user instanceof Manager) {
            tabs.addTab("Low Stock", createLowStockPanel());
            tabs.addTab("Catalog", createCatalogManagementPanel());
            tabs.addTab("Users", createUserManagementPanel());
            tabs.addTab("Reports", createReportsPanel());
            tabs.addTab("Completed Sales", createSalesHistoryPanel(true));
            tabs.addTab("My Profile", createEditProfilePanel());
        } else {
            tabs.addTab("Customers", createCustomersPanel());
            tabs.addTab("Process Sale", createProcessSalePanel());
            tabs.addTab("My Sales", createSalesHistoryPanel(false));
        }

        dashboardContainer.add(header, BorderLayout.NORTH);
        dashboardContainer.add(tabs, BorderLayout.CENTER);
        dashboardContainer.revalidate();
        dashboardContainer.repaint();
    }

    private void logout() {
        currentUser = null;
        dashboardContainer.removeAll();
        rootLayout.show(rootPanel, "LOGIN");
    }

    private JPanel createProductsPanel() {
        NonEditableTableModel model = new NonEditableTableModel(
            new Object[] {"Code", "Product", "Price", "Stock", "Category"}, 0
        );
        JTable table = new JTable(model);
        JButton refreshButton = new JButton("Refresh Products");
        refreshButton.addActionListener(event -> loadProducts(model));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(buildTopBar(refreshButton), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        loadProducts(model);
        return panel;
    }

    private JPanel createLowStockPanel() {
        NonEditableTableModel model = new NonEditableTableModel(
            new Object[] {"Code", "Product", "Price", "Stock", "Category"}, 0
        );
        JTable table = new JTable(model);
        JSpinner thresholdSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 1000, 1));
        JButton refreshButton = new JButton("Load");
        refreshButton.addActionListener(event -> loadLowStock(model, ((Number) thresholdSpinner.getValue()).intValue()));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Threshold"));
        top.add(thresholdSpinner);
        top.add(refreshButton);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        loadLowStock(model, 10);
        return panel;
    }

    private JPanel createCatalogManagementPanel() {
        JPanel forms = new JPanel(new GridLayout(1, 3, 12, 12));
        forms.add(createAddCategoryCard());
        forms.add(createAddProductCard());
        forms.add(createRestockCard());

        NonEditableTableModel productsModel = new NonEditableTableModel(
            new Object[] {"Code", "Product", "Price", "Stock", "Category"}, 0
        );
        JTable productsTable = new JTable(productsModel);
        JButton refreshButton = new JButton("Refresh Products");
        refreshButton.addActionListener(event -> loadProducts(productsModel));

        JPanel bottom = new JPanel(new BorderLayout(10, 10));
        bottom.add(buildTopBar(refreshButton), BorderLayout.NORTH);
        bottom.add(new JScrollPane(productsTable), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(forms, BorderLayout.NORTH);
        panel.add(bottom, BorderLayout.CENTER);

        loadProducts(productsModel);
        return panel;
    }

    private JPanel createUserManagementPanel() {
        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        JTextField fullNameField = new JTextField();
        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JComboBox<Role> roleCombo = new JComboBox<>(new Role[]{Role.CASHIER});
        JButton createButton = new JButton("Register User");

        form.add(new JLabel("Full name"));
        form.add(fullNameField);
        form.add(new JLabel("Username"));
        form.add(usernameField);
        form.add(new JLabel("Password"));
        form.add(passwordField);
        form.add(new JLabel("Role"));
        form.add(roleCombo);
        form.add(new JLabel());
        form.add(createButton);


        NonEditableTableModel usersModel = new NonEditableTableModel(
            new Object[] {"ID", "Full name", "Username", "Role", "Active"}, 0
        );
        JTable usersTable = new JTable(usersModel);
        JButton refreshButton = new JButton("Refresh Users");
        refreshButton.addActionListener(event -> loadUsers(usersModel));

        JButton deactivateButton = new JButton("Delete Selected Cashier");
        JButton promoteButton = new JButton("Promote Selected to Manager");

        deactivateButton.addActionListener(event -> {
            int selectedRow = usersTable.getSelectedRow();
            if (selectedRow < 0) {
                showMessage("Select a cashier row first.");
                return;
            }
            int userId = (int) usersModel.getValueAt(selectedRow, 0);
            String role = usersModel.getValueAt(selectedRow, 3).toString();
            if (!role.equals("CASHIER")) {
                showMessage("Only cashier accounts can be deleted.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Deactivate cashier ID " + userId + "? This cannot be undone.",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            try {
                userManagementService.deactivateCashier(userId);
                loadUsers(usersModel);
                showMessage("Cashier deactivated successfully.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        promoteButton.addActionListener(event -> {
            int selectedRow = usersTable.getSelectedRow();
            if (selectedRow < 0) {
                showMessage("Select a cashier row first.");
                return;
            }
            int userId = (int) usersModel.getValueAt(selectedRow, 0);
            String role = usersModel.getValueAt(selectedRow, 3).toString();
            if (!role.equals("CASHIER")) {
                showMessage("Only cashiers can be promoted to manager.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(frame,
                    "Promote user ID " + userId + " to Manager?",
                    "Confirm Promotion", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            try {
                userManagementService.promoteToManager(userId);
                loadUsers(usersModel);
                showMessage("User promoted to manager successfully.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });


        createButton.addActionListener(event -> {
            try {
                String fullName = fullNameField.getText().trim();
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword());
                Role role = (Role) roleCombo.getSelectedItem();
                if (fullName.isBlank() || username.isBlank() || password.isBlank() || role == null) {
                    showMessage("Please fill in all user fields.");
                    return;
                }
                userManagementService.registerUser(fullName, username, password, role);
                fullNameField.setText("");
                usernameField.setText("");
                passwordField.setText("");
                loadUsers(usersModel);
                showMessage("User registered successfully.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(wrapCard("Register New User", form), BorderLayout.NORTH);
        JPanel bottom = new JPanel(new BorderLayout(10, 10));
        bottom.add(buildTopBar(refreshButton, deactivateButton, promoteButton), BorderLayout.NORTH);
        bottom.add(new JScrollPane(usersTable), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.CENTER);

        loadUsers(usersModel);
        return panel;
    }

    private JPanel createReportsPanel() {
        NonEditableTableModel dailyModel = new NonEditableTableModel(
            new Object[] {"Date", "Cashier", "Sales Count", "Items Sold", "Revenue"}, 0
        );
        JTable dailyTable = new JTable(dailyModel);
        JTextField dateField = new JTextField(LocalDate.now().toString(), 10);
        JButton loadButton = new JButton("Load Summary");
        loadButton.addActionListener(event -> loadDailySalesSummary(dailyModel, dateField.getText().trim()));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Date (yyyy-mm-dd)"));
        top.add(dateField);
        top.add(loadButton);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(dailyTable), BorderLayout.CENTER);

        loadDailySalesSummary(dailyModel, LocalDate.now().toString());
        return panel;
    }

    private JPanel createCustomersPanel() {
        JPanel form = new JPanel(new GridLayout(3, 2, 8, 8));
        JTextField nameField = new JTextField();
        JTextField phoneField = new JTextField();
        JButton registerButton = new JButton("Register Customer");

        form.add(new JLabel("Full name"));
        form.add(nameField);
        form.add(new JLabel("Phone (optional)"));
        form.add(phoneField);
        form.add(new JLabel());
        form.add(registerButton);

        NonEditableTableModel customersModel = new NonEditableTableModel(
            new Object[] {"ID", "Full name", "Phone"}, 0
        );
        JTable customersTable = new JTable(customersModel);
        JButton refreshButton = new JButton("Refresh Customers");
        refreshButton.addActionListener(event -> loadCustomers(customersModel));

        registerButton.addActionListener(event -> {
            try {
                String fullName = nameField.getText().trim();
                String phone = phoneField.getText().trim();
                if (fullName.isBlank()) {
                    showMessage("Customer name is required.");
                    return;
                }
                salesService.registerCustomer(fullName, phone.isBlank() ? null : phone);
                nameField.setText("");
                phoneField.setText("");
                loadCustomers(customersModel);
                showMessage("Customer saved successfully.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(wrapCard("Register Customer", form), BorderLayout.NORTH);
        JPanel tablePanel = new JPanel(new BorderLayout(10, 10));
        tablePanel.add(buildTopBar(refreshButton), BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(customersTable), BorderLayout.CENTER);
        panel.add(tablePanel, BorderLayout.CENTER);

        loadCustomers(customersModel);
        return panel;
    }

    private JPanel createProcessSalePanel() {
        List<Product> initialProducts = safeGetProducts();
        List<Customer> initialCustomers = safeGetCustomers();
        List<SaleRequestItem> cart = new ArrayList<>();

        JComboBox<CustomerOption> customerCombo = new JComboBox<>();
        JComboBox<ProductOption> productCombo = new JComboBox<>();
        JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        JTextField paymentField = new JTextField();
        JTextArea summaryArea = new JTextArea(8, 30);
        summaryArea.setEditable(false);

        NonEditableTableModel cartModel = new NonEditableTableModel(
            new Object[] {"Code", "Product", "Quantity", "Unit Price", "Line Total"}, 0
        );
        JTable cartTable = new JTable(cartModel);

        JButton reloadDataButton = new JButton("Reload Products/Customers");
        JButton addItemButton = new JButton("Add Item");
        JButton clearCartButton = new JButton("Clear Cart");
        JButton processButton = new JButton("Process Sale");

        reloadComboModels(customerCombo, productCombo, initialCustomers, initialProducts);
        updateCartSummary(cartModel, cart, summaryArea, initialProducts);

        reloadDataButton.addActionListener(event -> {
            List<Product> products = safeGetProducts();
            List<Customer> customers = safeGetCustomers();
            reloadComboModels(customerCombo, productCombo, customers, products);
            updateCartSummary(cartModel, cart, summaryArea, products);
        });

        addItemButton.addActionListener(event -> {
            ProductOption productOption = (ProductOption) productCombo.getSelectedItem();
            if (productOption == null || productOption.product == null) {
                showMessage("Please select a product.");
                return;
            }
            int quantity = ((Number) quantitySpinner.getValue()).intValue();
            cart.add(new SaleRequestItem(productOption.product.getProductCode(), quantity));
            updateCartSummary(cartModel, cart, summaryArea, safeGetProducts());
        });

        clearCartButton.addActionListener(event -> {
            cart.clear();
            updateCartSummary(cartModel, cart, summaryArea, safeGetProducts());
        });

        processButton.addActionListener(event -> {
            if (cart.isEmpty()) {
                showMessage("Add at least one item before processing the sale.");
                return;
            }
            BigDecimal paymentAmount;
            try {
                paymentAmount = new BigDecimal(paymentField.getText().trim());
            } catch (NumberFormatException ex) {
                showMessage("Enter a valid payment amount.");
                return;
            }

            CustomerOption customerOption = (CustomerOption) customerCombo.getSelectedItem();
            Integer customerId = customerOption == null ? null : customerOption.customerId;
            SaleRequest saleRequest = new SaleRequest(customerId, currentUser.getUserId(), paymentAmount, new ArrayList<>(cart));
            try {
                int saleId = salesService.processSale(saleRequest);
                cart.clear();
                paymentField.setText("");
                updateCartSummary(cartModel, cart, summaryArea, safeGetProducts());
                showMessage("Sale completed successfully. Sale ID: " + saleId);
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        JPanel topControls = new JPanel(new GridLayout(2, 1, 8, 8));
        JPanel selectorRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectorRow.add(new JLabel("Customer"));
        selectorRow.add(customerCombo);
        selectorRow.add(Box.createHorizontalStrut(10));
        selectorRow.add(new JLabel("Product"));
        selectorRow.add(productCombo);
        selectorRow.add(new JLabel("Qty"));
        selectorRow.add(quantitySpinner);
        selectorRow.add(addItemButton);
        selectorRow.add(clearCartButton);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionRow.add(new JLabel("Payment Amount"));
        paymentField.setColumns(10);
        actionRow.add(paymentField);
        actionRow.add(processButton);
        actionRow.add(reloadDataButton);

        topControls.add(selectorRow);
        topControls.add(actionRow);

        JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(cartTable),
            new JScrollPane(summaryArea)
        );
        splitPane.setResizeWeight(0.7);

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(topControls, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSalesHistoryPanel(boolean managerView) {
        NonEditableTableModel salesModel = new NonEditableTableModel(
            new Object[] {"Sale ID", "Date", "Cashier", "Customer", "Total", "Payment", "Change"}, 0
        );
        JTable salesTable = new JTable(salesModel);
        salesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTextArea detailArea = new JTextArea();
        detailArea.setEditable(false);

        List<Sale> salesCache = new ArrayList<>();

        JButton refreshButton = new JButton(managerView ? "Refresh Completed Sales" : "Refresh My Sales");
        refreshButton.addActionListener(event -> {
            List<Sale> sales = managerView ? safeGetAllCompletedSales() : safeGetSalesForCurrentUser();
            salesCache.clear();
            salesCache.addAll(sales);
            loadSalesIntoTable(salesModel, sales);
            updateSaleDetails(detailArea, null);
        });

        salesTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            int index = salesTable.getSelectedRow();
            if (index >= 0 && index < salesCache.size()) {
                updateSaleDetails(detailArea, salesCache.get(index));
            }
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(buildTopBar(refreshButton), BorderLayout.NORTH);
        JSplitPane splitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(salesTable),
            new JScrollPane(detailArea)
        );
        splitPane.setResizeWeight(0.65);
        panel.add(splitPane, BorderLayout.CENTER);

        List<Sale> sales = managerView ? safeGetAllCompletedSales() : safeGetSalesForCurrentUser();
        salesCache.addAll(sales);
        loadSalesIntoTable(salesModel, sales);
        return panel;
    }

    private JPanel createAddCategoryCard() {
        JTextField categoryNameField = new JTextField();
        JButton addButton = new JButton("Add Category");
        JPanel form = new JPanel(new GridLayout(2, 1, 8, 8));
        form.add(categoryNameField);
        form.add(addButton);

        addButton.addActionListener(event -> {
            try {
                String categoryName = categoryNameField.getText().trim();
                if (categoryName.isBlank()) {
                    showMessage("Category name is required.");
                    return;
                }
                inventoryService.addCategory(categoryName);
                categoryNameField.setText("");
                showMessage("Category created successfully.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        return wrapCard("Add Category", form);
    }

    private JPanel createAddProductCard() {
        JTextField codeField = new JTextField();
        JTextField nameField = new JTextField();
        JTextField priceField = new JTextField();
        JTextField stockField = new JTextField();
        JComboBox<CategoryOption> categoryCombo = new JComboBox<>();
        JButton reloadCategoriesButton = new JButton("Reload Categories");
        JButton addButton = new JButton("Add Product");

        loadCategoryOptions(categoryCombo);
        reloadCategoriesButton.addActionListener(event -> loadCategoryOptions(categoryCombo));

        JPanel form = new JPanel(new GridLayout(7, 1, 8, 8));
        form.add(codeField);
        form.add(nameField);
        form.add(priceField);
        form.add(stockField);
        form.add(categoryCombo);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(addButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(reloadCategoriesButton);
        form.add(buttons);
        form.add(new JLabel("Fields: code, name, price, stock, category"));

        addButton.addActionListener(event -> {
            try {
                CategoryOption categoryOption = (CategoryOption) categoryCombo.getSelectedItem();
                if (categoryOption == null) {
                    showMessage("Choose a category first.");
                    return;
                }
                inventoryService.addProduct(
                    codeField.getText().trim(),
                    nameField.getText().trim(),
                    new BigDecimal(priceField.getText().trim()),
                    Integer.parseInt(stockField.getText().trim()),
                    categoryOption.category
                );
                codeField.setText("");
                nameField.setText("");
                priceField.setText("");
                stockField.setText("");
                showMessage("Product added successfully.");
            } catch (NumberFormatException ex) {
                showMessage("Price and stock must be valid numbers.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        return wrapCard("Add Product", form);
    }

    private JPanel createRestockCard() {
        JTextField productCodeField = new JTextField();
        JTextField quantityField = new JTextField();
        JButton restockButton = new JButton("Restock Product");

        JPanel form = new JPanel(new GridLayout(3, 1, 8, 8));
        form.add(productCodeField);
        form.add(quantityField);
        form.add(restockButton);
        form.add(new JLabel("Fields: product code, quantity"));

        restockButton.addActionListener(event -> {
            try {
                inventoryService.restockProduct(productCodeField.getText().trim(), Integer.parseInt(quantityField.getText().trim()));
                productCodeField.setText("");
                quantityField.setText("");
                showMessage("Product restocked successfully.");
            } catch (NumberFormatException ex) {
                showMessage("Quantity must be a valid whole number.");
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        return wrapCard("Restock", form);
    }

    private JPanel createEditProfilePanel() {
        JTextField fullNameField = new JTextField(currentUser.getFullName(), 20);
        JPasswordField newPasswordField = new JPasswordField(20);
        JPasswordField confirmPasswordField = new JPasswordField(20);
        JButton saveButton = new JButton("Save Changes");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets.set(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Full Name"), gbc);
        gbc.gridx = 1;
        form.add(fullNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("New Password"), gbc);
        gbc.gridx = 1;
        form.add(newPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(new JLabel("Confirm Password"), gbc);
        gbc.gridx = 1;
        form.add(confirmPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        form.add(saveButton, gbc);

        saveButton.addActionListener(event -> {
            String fullName = fullNameField.getText().trim();
            String newPassword = new String(newPasswordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (fullName.isBlank()) {
                showMessage("Full name cannot be empty.");
                return;
            }
            if (newPassword.isBlank()) {
                showMessage("New password cannot be empty.");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                showMessage("Passwords do not match.");
                return;
            }
            try {
                userManagementService.updateManagerProfile(
                        currentUser.getUserId(), fullName, newPassword);
                showMessage("Profile updated successfully. Please log in again.");
                logout();
            } catch (SQLException ex) {
                showDatabaseError(ex);
            }
        });

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(wrapCard("Edit My Profile", form), BorderLayout.NORTH);
        return panel;
    }
    
    private JPanel wrapCard(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildTopBar(JComponent... components) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (JComponent component : components) {
            bar.add(component);
        }
        return bar;
    }

    private void loadProducts(DefaultTableModel model) {
        try {
            List<Product> products = inventoryService.listProducts();
            model.setRowCount(0);
            for (Product product : products) {
                model.addRow(new Object[] {
                    product.getProductCode(),
                    product.getProductName(),
                    product.getUnitPrice(),
                    product.getStockQuantity(),
                    product.getCategory().getCategoryName()
                });
            }
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void loadLowStock(DefaultTableModel model, int threshold) {
        try {
            List<Product> products = inventoryService.listLowStockProducts(threshold);
            model.setRowCount(0);
            for (Product product : products) {
                model.addRow(new Object[] {
                    product.getProductCode(),
                    product.getProductName(),
                    product.getUnitPrice(),
                    product.getStockQuantity(),
                    product.getCategory().getCategoryName()
                });
            }
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void loadUsers(DefaultTableModel model) {
        try {
            List<AbstractUser> users = userManagementService.listUsers();
            model.setRowCount(0);
            for (AbstractUser user : users) {
                model.addRow(new Object[] {
                    user.getUserId(),
                    user.getFullName(),
                    user.getUsername(),
                    user.getRole(),
                    user.isActive()
                });
            }
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void loadCustomers(DefaultTableModel model) {
        try {
            List<Customer> customers = salesService.listCustomers();
            model.setRowCount(0);
            for (Customer customer : customers) {
                model.addRow(new Object[] {
                    customer.getCustomerId(),
                    customer.getFullName(),
                    customer.getPhone()
                });
            }
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void loadDailySalesSummary(DefaultTableModel model, String rawDate) {
        try {
            LocalDate salesDate = LocalDate.parse(rawDate);
            List<DailySalesSummary> summaries = reportingService.getDailySalesSummary(salesDate);
            model.setRowCount(0);
            for (DailySalesSummary summary : summaries) {
                model.addRow(new Object[] {
                    summary.getSalesDate(),
                    summary.getCashierName(),
                    summary.getSalesCount(),
                    summary.getItemsSold(),
                    summary.getTotalRevenue()
                });
            }
        } catch (DateTimeParseException ex) {
            showMessage("Invalid date. Use yyyy-mm-dd.");
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void loadSalesIntoTable(DefaultTableModel model, List<Sale> sales) {
        model.setRowCount(0);
        for (Sale sale : sales) {
            String customerName = sale.getCustomer() == null ? "Walk-in / not recorded" : sale.getCustomer().getFullName();
            model.addRow(new Object[] {
                sale.getSaleId(),
                sale.getSaleDate().format(DATE_TIME_FORMATTER),
                sale.getCashier().getFullName(),
                customerName,
                sale.calculateTotal(),
                sale.getPaymentAmount(),
                sale.calculateChange()
            });
        }
    }

    private void updateSaleDetails(JTextArea detailArea, Sale sale) {
        if (sale == null) {
            detailArea.setText("Select a sale row to view full line-item details.");
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Sale #").append(sale.getSaleId()).append(System.lineSeparator());
        builder.append("Date: ").append(sale.getSaleDate().format(DATE_TIME_FORMATTER)).append(System.lineSeparator());
        builder.append("Cashier: ").append(sale.getCashier().getFullName()).append(System.lineSeparator());
        builder.append("Customer: ").append(sale.getCustomer() == null ? "Walk-in / not recorded" : sale.getCustomer().getFullName()).append(System.lineSeparator());
        builder.append("Status: ").append(sale.getStatus()).append(System.lineSeparator());
        builder.append("Total: ").append(sale.calculateTotal()).append(System.lineSeparator());
        builder.append("Payment: ").append(sale.getPaymentAmount()).append(System.lineSeparator());
        builder.append("Change: ").append(sale.calculateChange()).append(System.lineSeparator());
        builder.append(System.lineSeparator()).append("Items").append(System.lineSeparator());
        for (SaleItem item : sale.getItems()) {
            builder.append("- ")
                .append(item.getProduct().getProductName())
                .append(" | qty=")
                .append(item.getQuantity())
                .append(" | unit=")
                .append(item.getUnitPrice())
                .append(" | total=")
                .append(item.calculateTotal())
                .append(System.lineSeparator());
        }
        detailArea.setText(builder.toString());
        detailArea.setCaretPosition(0);
    }

    private void loadCategoryOptions(JComboBox<CategoryOption> comboBox) {
        try {
            List<Category> categories = inventoryService.listCategories();
            DefaultComboBoxModel<CategoryOption> model = new DefaultComboBoxModel<>();
            for (Category category : categories) {
                model.addElement(new CategoryOption(category));
            }
            comboBox.setModel(model);
        } catch (SQLException ex) {
            showDatabaseError(ex);
        }
    }

    private void reloadComboModels(JComboBox<CustomerOption> customerCombo, JComboBox<ProductOption> productCombo,
                                   List<Customer> customers, List<Product> products) {
        DefaultComboBoxModel<CustomerOption> customerModel = new DefaultComboBoxModel<>();
        customerModel.addElement(new CustomerOption(null));
        for (Customer customer : customers) {
            customerModel.addElement(new CustomerOption(customer));
        }
        customerCombo.setModel(customerModel);

        DefaultComboBoxModel<ProductOption> productModel = new DefaultComboBoxModel<>();
        for (Product product : products) {
            productModel.addElement(new ProductOption(product));
        }
        productCombo.setModel(productModel);
    }

    private void updateCartSummary(DefaultTableModel cartModel, List<SaleRequestItem> cart, JTextArea summaryArea,
                                   List<Product> products) {
        cartModel.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder builder = new StringBuilder();
        for (SaleRequestItem requestItem : cart) {
            Product product = products.stream()
                .filter(item -> item.getProductCode().equalsIgnoreCase(requestItem.getProductCode()))
                .findFirst()
                .orElse(null);
            if (product == null) {
                continue;
            }
            BigDecimal lineTotal = product.getUnitPrice().multiply(BigDecimal.valueOf(requestItem.getQuantity()));
            total = total.add(lineTotal);
            cartModel.addRow(new Object[] {
                product.getProductCode(),
                product.getProductName(),
                requestItem.getQuantity(),
                product.getUnitPrice(),
                lineTotal
            });
            builder.append(product.getProductName())
                .append(" x")
                .append(requestItem.getQuantity())
                .append(" = ")
                .append(lineTotal)
                .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("Current total: ").append(total);
        summaryArea.setText(builder.toString());
        summaryArea.setCaretPosition(0);
    }

    private List<Product> safeGetProducts() {
        try {
            return inventoryService.listProducts();
        } catch (SQLException ex) {
            showDatabaseError(ex);
            return new ArrayList<>();
        }
    }

    private List<Customer> safeGetCustomers() {
        try {
            return salesService.listCustomers();
        } catch (SQLException ex) {
            showDatabaseError(ex);
            return new ArrayList<>();
        }
    }

    private List<Sale> safeGetAllCompletedSales() {
        try {
            return salesService.listAllCompletedSales();
        } catch (SQLException ex) {
            showDatabaseError(ex);
            return new ArrayList<>();
        }
    }

    private List<Sale> safeGetSalesForCurrentUser() {
        try {
            return salesService.listSalesForCashier(currentUser.getUserId());
        } catch (SQLException ex) {
            showDatabaseError(ex);
            return new ArrayList<>();
        }
    }

    private void showDatabaseError(SQLException ex) {
        String message = "Database error: " + ex.getMessage() + System.lineSeparator()
            + "Make sure SQL Server is running, the schema is loaded, and the JDBC driver jar is on the classpath.";
        JOptionPane.showMessageDialog(frame, message, "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(frame, message, "Mini Supermarket System", JOptionPane.INFORMATION_MESSAGE);
    }

    private static class NonEditableTableModel extends DefaultTableModel {
        private NonEditableTableModel(Object[] columns, int rowCount) {
            super(columns, rowCount);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private static class CategoryOption {
        private final Category category;

        private CategoryOption(Category category) {
            this.category = category;
        }

        @Override
        public String toString() {
            return category.getCategoryName() + " (ID " + category.getCategoryId() + ")";
        }
    }

    private static class CustomerOption {
        private final Integer customerId;
        private final String label;

        private CustomerOption(Customer customer) {
            if (customer == null) {
                this.customerId = null;
                this.label = "Walk-in / not recorded";
            } else {
                this.customerId = customer.getCustomerId();
                this.label = customer.toString();
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class ProductOption {
        private final Product product;

        private ProductOption(Product product) {
            this.product = product;
        }

        @Override
        public String toString() {
            return product.getProductName() + " (" + product.getProductCode() + ")";
        }
    }
}
