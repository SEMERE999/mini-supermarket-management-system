IF DB_ID('MiniSupermarketDB') IS NULL
BEGIN
    CREATE DATABASE MiniSupermarketDB;
END
GO

USE MiniSupermarketDB;
GO

IF OBJECT_ID('dbo.vw_DailySalesSummary', 'V') IS NOT NULL DROP VIEW dbo.vw_DailySalesSummary;
GO
IF OBJECT_ID('dbo.fn_GetProductStockValue', 'FN') IS NOT NULL DROP FUNCTION dbo.fn_GetProductStockValue;
GO
IF OBJECT_ID('dbo.trg_SaleItem_RecalculateTotal', 'TR') IS NOT NULL DROP TRIGGER dbo.trg_SaleItem_RecalculateTotal;
GO
IF OBJECT_ID('dbo.trg_Sale_CompleteAndReduceStock', 'TR') IS NOT NULL DROP TRIGGER dbo.trg_Sale_CompleteAndReduceStock;
GO
IF OBJECT_ID('dbo.sp_RegisterUser', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_RegisterUser;
GO
IF OBJECT_ID('dbo.sp_AddCategory', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_AddCategory;
GO
IF OBJECT_ID('dbo.sp_AddProduct', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_AddProduct;
GO
IF OBJECT_ID('dbo.sp_RestockProduct', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_RestockProduct;
GO
IF OBJECT_ID('dbo.sp_CreateSale', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_CreateSale;
GO
IF OBJECT_ID('dbo.sp_AddSaleItem', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_AddSaleItem;
GO
IF OBJECT_ID('dbo.sp_FinalizeSale', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_FinalizeSale;
GO
IF OBJECT_ID('dbo.sp_CancelSale', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_CancelSale;
GO

IF OBJECT_ID('dbo.SaleItem', 'U') IS NOT NULL DROP TABLE dbo.SaleItem;
IF OBJECT_ID('dbo.Sale', 'U') IS NOT NULL DROP TABLE dbo.Sale;
IF OBJECT_ID('dbo.Product', 'U') IS NOT NULL DROP TABLE dbo.Product;
IF OBJECT_ID('dbo.Customer', 'U') IS NOT NULL DROP TABLE dbo.Customer;
IF OBJECT_ID('dbo.Category', 'U') IS NOT NULL DROP TABLE dbo.Category;
IF OBJECT_ID('dbo.AppUser', 'U') IS NOT NULL DROP TABLE dbo.AppUser;
GO

CREATE TABLE dbo.AppUser
(
    UserId INT IDENTITY(1,1) PRIMARY KEY,
    FullName VARCHAR(100) NOT NULL,
    Username VARCHAR(50) NOT NULL UNIQUE,
    PasswordHash VARCHAR(64) NOT NULL,
    Role VARCHAR(20) NOT NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_AppUser_IsActive DEFAULT 1,
    CreatedAt DATETIME2 NOT NULL CONSTRAINT DF_AppUser_CreatedAt DEFAULT SYSDATETIME(),
    CONSTRAINT CK_AppUser_Role CHECK (Role IN ('MANAGER', 'CASHIER'))
);
GO

CREATE TABLE dbo.Category
(
    CategoryId INT IDENTITY(1,1) PRIMARY KEY,
    CategoryName VARCHAR(100) NOT NULL UNIQUE
);
GO

CREATE TABLE dbo.Customer
(
    CustomerId INT IDENTITY(1,1) PRIMARY KEY,
    FullName VARCHAR(100) NOT NULL,
    Phone VARCHAR(20) NULL UNIQUE
);
GO

CREATE TABLE dbo.Product
(
    ProductCode VARCHAR(20) PRIMARY KEY,
    ProductName VARCHAR(100) NOT NULL,
    UnitPrice DECIMAL(10, 2) NOT NULL,
    StockQuantity INT NOT NULL,
    CategoryId INT NOT NULL,
    IsActive BIT NOT NULL CONSTRAINT DF_Product_IsActive DEFAULT 1,
    CONSTRAINT CK_Product_Price CHECK (UnitPrice > 0),
    CONSTRAINT CK_Product_Stock CHECK (StockQuantity >= 0),
    CONSTRAINT FK_Product_Category FOREIGN KEY (CategoryId)
        REFERENCES dbo.Category(CategoryId)
        ON UPDATE CASCADE
);
GO

CREATE TABLE dbo.Sale
(
    SaleId INT IDENTITY(1,1) PRIMARY KEY,
    SaleDate DATETIME2 NOT NULL CONSTRAINT DF_Sale_SaleDate DEFAULT SYSDATETIME(),
    TotalPrice DECIMAL(10, 2) NOT NULL CONSTRAINT DF_Sale_TotalPrice DEFAULT 0,
    PaymentAmount DECIMAL(10, 2) NOT NULL CONSTRAINT DF_Sale_PaymentAmount DEFAULT 0,
    CustomerId INT NULL,
    CashierId INT NOT NULL,
    Status VARCHAR(20) NOT NULL CONSTRAINT DF_Sale_Status DEFAULT 'PENDING',
    CONSTRAINT CK_Sale_TotalPrice CHECK (TotalPrice >= 0),
    CONSTRAINT CK_Sale_PaymentAmount CHECK (PaymentAmount >= 0),
    CONSTRAINT CK_Sale_Status CHECK (Status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT FK_Sale_Customer FOREIGN KEY (CustomerId)
        REFERENCES dbo.Customer(CustomerId),
    CONSTRAINT FK_Sale_Cashier FOREIGN KEY (CashierId)
        REFERENCES dbo.AppUser(UserId)
);
GO

CREATE TABLE dbo.SaleItem
(
    SaleItemId INT IDENTITY(1,1) PRIMARY KEY,
    SaleId INT NOT NULL,
    ProductCode VARCHAR(20) NOT NULL,
    Quantity INT NOT NULL,
    UnitPrice DECIMAL(10, 2) NOT NULL,
    LineTotal AS CAST(Quantity * UnitPrice AS DECIMAL(10, 2)) PERSISTED,
    CONSTRAINT CK_SaleItem_Quantity CHECK (Quantity > 0),
    CONSTRAINT CK_SaleItem_UnitPrice CHECK (UnitPrice > 0),
    CONSTRAINT UQ_SaleItem UNIQUE (SaleId, ProductCode),
    CONSTRAINT FK_SaleItem_Sale FOREIGN KEY (SaleId)
        REFERENCES dbo.Sale(SaleId)
        ON DELETE CASCADE,
    CONSTRAINT FK_SaleItem_Product FOREIGN KEY (ProductCode)
        REFERENCES dbo.Product(ProductCode)
);
GO

CREATE TRIGGER dbo.trg_SaleItem_RecalculateTotal
ON dbo.SaleItem
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @AffectedSales TABLE (SaleId INT PRIMARY KEY);

    INSERT INTO @AffectedSales (SaleId)
    SELECT DISTINCT SaleId
    FROM inserted
    WHERE SaleId IS NOT NULL
    UNION
    SELECT DISTINCT SaleId
    FROM deleted
    WHERE SaleId IS NOT NULL;

    UPDATE s
    SET TotalPrice = ISNULL(t.TotalPrice, 0)
    FROM dbo.Sale AS s
    INNER JOIN @AffectedSales AS a ON a.SaleId = s.SaleId
    OUTER APPLY
    (
        SELECT SUM(LineTotal) AS TotalPrice
        FROM dbo.SaleItem
        WHERE SaleId = s.SaleId
    ) AS t;
END;
GO

CREATE TRIGGER dbo.trg_Sale_CompleteAndReduceStock
ON dbo.Sale
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT UPDATE(Status)
    BEGIN
        RETURN;
    END;

    IF EXISTS
    (
        SELECT 1
        FROM inserted AS i
        INNER JOIN deleted AS d ON d.SaleId = i.SaleId
        WHERE d.Status = 'COMPLETED'
          AND i.Status <> 'COMPLETED'
    )
    BEGIN
        THROW 50001, 'Completed sales cannot be reopened or cancelled.', 1;
    END;

    IF EXISTS
    (
        SELECT 1
        FROM inserted AS i
        INNER JOIN deleted AS d ON d.SaleId = i.SaleId
        WHERE d.Status <> 'COMPLETED'
          AND i.Status = 'COMPLETED'
          AND NOT EXISTS (SELECT 1 FROM dbo.SaleItem AS si WHERE si.SaleId = i.SaleId)
    )
    BEGIN
        THROW 50002, 'A sale must include at least one product before completion.', 1;
    END;

    IF EXISTS
    (
        SELECT 1
        FROM inserted AS i
        INNER JOIN deleted AS d ON d.SaleId = i.SaleId
        WHERE d.Status <> 'COMPLETED'
          AND i.Status = 'COMPLETED'
          AND i.PaymentAmount < i.TotalPrice
    )
    BEGIN
        THROW 50003, 'Payment amount must be greater than or equal to the total price.', 1;
    END;

    IF EXISTS
    (
        SELECT 1
        FROM inserted AS i
        INNER JOIN deleted AS d ON d.SaleId = i.SaleId
        INNER JOIN dbo.SaleItem AS si ON si.SaleId = i.SaleId
        INNER JOIN dbo.Product AS p ON p.ProductCode = si.ProductCode
        WHERE d.Status <> 'COMPLETED'
          AND i.Status = 'COMPLETED'
        GROUP BY i.SaleId, p.ProductCode, p.StockQuantity
        HAVING SUM(si.Quantity) > p.StockQuantity
    )
    BEGIN
        THROW 50004, 'Sold quantity cannot exceed available stock.', 1;
    END;

    ;WITH CompletedSales AS
    (
        SELECT i.SaleId
        FROM inserted AS i
        INNER JOIN deleted AS d ON d.SaleId = i.SaleId
        WHERE d.Status <> 'COMPLETED'
          AND i.Status = 'COMPLETED'
    ),
    StockUsage AS
    (
        SELECT si.ProductCode, SUM(si.Quantity) AS QuantityToDeduct
        FROM dbo.SaleItem AS si
        INNER JOIN CompletedSales AS cs ON cs.SaleId = si.SaleId
        GROUP BY si.ProductCode
    )
    UPDATE p
    SET p.StockQuantity = p.StockQuantity - su.QuantityToDeduct
    FROM dbo.Product AS p
    INNER JOIN StockUsage AS su ON su.ProductCode = p.ProductCode;
END;
GO

CREATE FUNCTION dbo.fn_GetProductStockValue
(
    @ProductCode VARCHAR(20)
)
RETURNS DECIMAL(12, 2)
AS
BEGIN
    DECLARE @StockValue DECIMAL(12, 2);

    SELECT @StockValue = CAST(StockQuantity * UnitPrice AS DECIMAL(12, 2))
    FROM dbo.Product
    WHERE ProductCode = @ProductCode;

    RETURN ISNULL(@StockValue, 0);
END;
GO

CREATE VIEW dbo.vw_DailySalesSummary
AS
SELECT
    CAST(s.SaleDate AS DATE) AS SalesDate,
    u.FullName AS CashierName,
    COUNT(DISTINCT s.SaleId) AS SalesCount,
    SUM(s.TotalPrice) AS TotalRevenue,
    SUM(si.Quantity) AS ItemsSold
FROM dbo.Sale AS s
INNER JOIN dbo.AppUser AS u ON u.UserId = s.CashierId
INNER JOIN dbo.SaleItem AS si ON si.SaleId = s.SaleId
WHERE s.Status = 'COMPLETED'
GROUP BY CAST(s.SaleDate AS DATE), u.FullName;
GO

CREATE PROCEDURE dbo.sp_RegisterUser
    @FullName VARCHAR(100),
    @Username VARCHAR(50),
    @PasswordHash VARCHAR(64),
    @Role VARCHAR(20)
AS
BEGIN
    SET NOCOUNT ON;

    IF @Role NOT IN ('MANAGER', 'CASHIER')
    BEGIN
        THROW 50005, 'Role must be either MANAGER or CASHIER.', 1;
    END;

    INSERT INTO dbo.AppUser (FullName, Username, PasswordHash, Role)
    VALUES (@FullName, @Username, @PasswordHash, @Role);
END;
GO

GO

CREATE PROCEDURE dbo.sp_AddCategory
    @CategoryName VARCHAR(100)
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.Category (CategoryName)
    VALUES (@CategoryName);
END;
GO

CREATE PROCEDURE dbo.sp_AddProduct
    @ProductCode VARCHAR(20),
    @ProductName VARCHAR(100),
    @UnitPrice DECIMAL(10, 2),
    @StockQuantity INT,
    @CategoryId INT
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.Product (ProductCode, ProductName, UnitPrice, StockQuantity, CategoryId)
    VALUES (@ProductCode, @ProductName, @UnitPrice, @StockQuantity, @CategoryId);
END;
GO

CREATE PROCEDURE dbo.sp_RestockProduct
    @ProductCode VARCHAR(20),
    @QuantityToAdd INT
AS
BEGIN
    SET NOCOUNT ON;

    IF @QuantityToAdd <= 0
    BEGIN
        THROW 50006, 'Restock quantity must be greater than zero.', 1;
    END;

    UPDATE dbo.Product
    SET StockQuantity = StockQuantity + @QuantityToAdd
    WHERE ProductCode = @ProductCode;

    IF @@ROWCOUNT = 0
    BEGIN
        THROW 50007, 'Product not found.', 1;
    END;
END;
GO

CREATE PROCEDURE dbo.sp_CreateSale
    @CustomerId INT = NULL,
    @CashierId INT,
    @PaymentAmount DECIMAL(10, 2),
    @SaleId INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.Sale (CustomerId, CashierId, PaymentAmount, Status)
    VALUES (@CustomerId, @CashierId, @PaymentAmount, 'PENDING');

    SET @SaleId = SCOPE_IDENTITY();
END;
GO

CREATE PROCEDURE dbo.sp_AddSaleItem
    @SaleId INT,
    @ProductCode VARCHAR(20),
    @Quantity INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Status VARCHAR(20);
    DECLARE @UnitPrice DECIMAL(10, 2);

    SELECT @Status = Status
    FROM dbo.Sale
    WHERE SaleId = @SaleId;

    IF @Status IS NULL
    BEGIN
        THROW 50008, 'Sale not found.', 1;
    END;

    IF @Status <> 'PENDING'
    BEGIN
        THROW 50009, 'Items can only be added to a pending sale.', 1;
    END;

    SELECT @UnitPrice = UnitPrice
    FROM dbo.Product
    WHERE ProductCode = @ProductCode
      AND IsActive = 1;

    IF @UnitPrice IS NULL
    BEGIN
        THROW 50010, 'Active product not found.', 1;
    END;

    IF EXISTS
    (
        SELECT 1
        FROM dbo.SaleItem
        WHERE SaleId = @SaleId
          AND ProductCode = @ProductCode
    )
    BEGIN
        UPDATE dbo.SaleItem
        SET Quantity = Quantity + @Quantity
        WHERE SaleId = @SaleId
          AND ProductCode = @ProductCode;
    END
    ELSE
    BEGIN
        INSERT INTO dbo.SaleItem (SaleId, ProductCode, Quantity, UnitPrice)
        VALUES (@SaleId, @ProductCode, @Quantity, @UnitPrice);
    END
END;
GO

CREATE PROCEDURE dbo.sp_FinalizeSale
    @SaleId INT,
    @PaymentAmount DECIMAL(10, 2)
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.Sale
    SET PaymentAmount = @PaymentAmount,
        Status = 'COMPLETED'
    WHERE SaleId = @SaleId
      AND Status = 'PENDING';

    IF @@ROWCOUNT = 0
    BEGIN
        THROW 50011, 'Pending sale not found for finalization.', 1;
    END;
END;
GO

CREATE PROCEDURE dbo.sp_CancelSale
    @SaleId INT
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.Sale
    SET Status = 'CANCELLED'
    WHERE SaleId = @SaleId
      AND Status = 'PENDING';
END;
GO

CREATE PROCEDURE dbo.sp_DeactivateUser
    @UserId INT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.AppUser WHERE UserId = @UserId AND Role = 'CASHIER')
    BEGIN
        THROW 50012, 'Only cashier accounts can be deactivated.', 1;
    END;

    UPDATE dbo.AppUser
    SET IsActive = 0
    WHERE UserId = @UserId;

    IF @@ROWCOUNT = 0
    BEGIN
        THROW 50013, 'User not found.', 1;
    END;
END;
GO

CREATE PROCEDURE dbo.sp_UpdateManagerProfile
    @UserId      INT,
    @FullName    VARCHAR(100),
    @NewPasswordHash VARCHAR(64)
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.AppUser WHERE UserId = @UserId AND Role = 'MANAGER')
    BEGIN
        THROW 50014, 'Only managers can use this procedure.', 1;
    END;

    UPDATE dbo.AppUser
    SET FullName     = @FullName,
        PasswordHash = @NewPasswordHash
    WHERE UserId = @UserId;
END;
GO

CREATE PROCEDURE dbo.sp_PromoteToManager
    @UserId INT
AS
BEGIN
    SET NOCOUNT ON;

    IF NOT EXISTS (SELECT 1 FROM dbo.AppUser WHERE UserId = @UserId AND Role = 'CASHIER' AND IsActive = 1)
    BEGIN
        THROW 50015, 'Only active cashiers can be promoted to manager.', 1;
    END;

    UPDATE dbo.AppUser
    SET Role = 'MANAGER'
    WHERE UserId = @UserId;
END;
GO

DECLARE @ManagerPasswordHash VARCHAR(64) = CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', 'Manager@123'), 2);
EXEC dbo.sp_RegisterUser
    @FullName = 'Store Manager',
    @Username = 'manager1',
    @PasswordHash = @ManagerPasswordHash,
    @Role = 'MANAGER';
GO
DECLARE @CashierPasswordHash1 VARCHAR(64) = CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', 'Cashier@123'), 2);
EXEC dbo.sp_RegisterUser
    @FullName = 'Aster Kassahun',
    @Username = 'cashier1',
    @PasswordHash = @CashierPasswordHash1,
    @Role = 'CASHIER';
GO
DECLARE @CashierPasswordHash2 VARCHAR(64) = CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', 'Cashier@123'), 2);
EXEC dbo.sp_RegisterUser
    @FullName = 'Samuel Tadesse',
    @Username = 'cashier2',
    @PasswordHash = @CashierPasswordHash2,
    @Role = 'CASHIER';
GO
DECLARE @CashierPasswordHash3 VARCHAR(64) = CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', 'Cashier@123'), 2);
EXEC dbo.sp_RegisterUser
    @FullName     = 'Tigist Haile',
    @Username     = 'cashier3',
    @PasswordHash = @CashierPasswordHash3,
    @Role         = 'CASHIER';
GO

DECLARE @CashierPasswordHash4 VARCHAR(64) = CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', 'Cashier@123'), 2);
EXEC dbo.sp_RegisterUser
    @FullName     = 'Dawit Bekele',
    @Username     = 'cashier4',
    @PasswordHash = @CashierPasswordHash4,
    @Role         = 'CASHIER';
GO

EXEC dbo.sp_AddCategory @CategoryName = 'Grocery';
EXEC dbo.sp_AddCategory @CategoryName = 'Beverage';
EXEC dbo.sp_AddCategory @CategoryName = 'Household';
EXEC dbo.sp_AddCategory @CategoryName = 'Personal Care';
EXEC dbo.sp_AddCategory @CategoryName = 'Dairy & Eggs';
EXEC dbo.sp_AddCategory @CategoryName = 'Bakery';
EXEC dbo.sp_AddCategory @CategoryName = 'Snacks & Confectionery';
EXEC dbo.sp_AddCategory @CategoryName = 'Spices & Condiments';
EXEC dbo.sp_AddCategory @CategoryName = 'Grains & Pulses';
EXEC dbo.sp_AddCategory @CategoryName = 'Meat & Poultry';
EXEC dbo.sp_AddCategory @CategoryName = 'Frozen & Chilled';
EXEC dbo.sp_AddCategory @CategoryName = 'Baby & Child';
GO

EXEC dbo.sp_AddProduct @ProductCode = 'PRD001', @ProductName = 'Rice 1kg', @UnitPrice = 95.00, @StockQuantity = 40, @CategoryId = 1;
EXEC dbo.sp_AddProduct @ProductCode = 'PRD002', @ProductName = 'Cooking Oil 1L', @UnitPrice = 210.00, @StockQuantity = 25, @CategoryId = 1;
EXEC dbo.sp_AddProduct @ProductCode = 'PRD003', @ProductName = 'Bottled Water 1.5L', @UnitPrice = 25.00, @StockQuantity = 80, @CategoryId = 2;
EXEC dbo.sp_AddProduct @ProductCode = 'PRD004', @ProductName = 'Orange Juice 1L', @UnitPrice = 85.00, @StockQuantity = 30, @CategoryId = 2;
EXEC dbo.sp_AddProduct @ProductCode = 'PRD005', @ProductName = 'Laundry Soap', @UnitPrice = 45.00, @StockQuantity = 35, @CategoryId = 3;
EXEC dbo.sp_AddProduct @ProductCode = 'PRD006', @ProductName = 'Toothpaste', @UnitPrice = 60.00, @StockQuantity = 45, @CategoryId = 4;
EXEC dbo.sp_AddProduct @ProductCode='PRD007', @ProductName='Wheat Flour 2kg',         @UnitPrice=110.00, @StockQuantity=50, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD008', @ProductName='Sugar 1kg',               @UnitPrice=70.00,  @StockQuantity=60, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD009', @ProductName='Salt 1kg',                @UnitPrice=25.00,  @StockQuantity=70, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD010', @ProductName='Pasta 500g',              @UnitPrice=55.00,  @StockQuantity=45, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD011', @ProductName='Lentils (Misir) 1kg',     @UnitPrice=90.00,  @StockQuantity=40, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD012', @ProductName='Chickpeas (Shimbra) 1kg', @UnitPrice=100.00, @StockQuantity=35, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD013', @ProductName='Tomato Paste 400g',       @UnitPrice=65.00,  @StockQuantity=50, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD014', @ProductName='Canned Tuna 185g',        @UnitPrice=120.00, @StockQuantity=30, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD015', @ProductName='Honey 500g',              @UnitPrice=280.00, @StockQuantity=20, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD016', @ProductName='Peanut Butter 400g',      @UnitPrice=175.00, @StockQuantity=25, @CategoryId=1;
EXEC dbo.sp_AddProduct @ProductCode='PRD017', @ProductName='Coca-Cola 500ml',         @UnitPrice=30.00,  @StockQuantity=100, @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD018', @ProductName='Pepsi 500ml',             @UnitPrice=30.00,  @StockQuantity=100, @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD019', @ProductName='Mirinda 500ml',           @UnitPrice=28.00,  @StockQuantity=80,  @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD020', @ProductName='Ambo Mineral Water 1L',   @UnitPrice=22.00,  @StockQuantity=120, @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD021', @ProductName='Bora Water 600ml',        @UnitPrice=15.00,  @StockQuantity=150, @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD022', @ProductName='Kool Juice Mango 250ml',  @UnitPrice=20.00,  @StockQuantity=90,  @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD023', @ProductName='Desta Milk Tea 330ml',    @UnitPrice=35.00,  @StockQuantity=60,  @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD024', @ProductName='Abyssinia Coffee 250g',   @UnitPrice=220.00, @StockQuantity=30,  @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD025', @ProductName='Wush Wush Tea Bags 25pc', @UnitPrice=85.00,  @StockQuantity=40,  @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD026', @ProductName='Jebena Coffee Blend 500g',@UnitPrice=320.00, @StockQuantity=20,  @CategoryId=2;
EXEC dbo.sp_AddProduct @ProductCode='PRD027', @ProductName='Omo Detergent 1kg',        @UnitPrice=130.00, @StockQuantity=40, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD028', @ProductName='Ariel Detergent 500g',     @UnitPrice=95.00,  @StockQuantity=35, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD029', @ProductName='Jik Bleach 750ml',         @UnitPrice=75.00,  @StockQuantity=30, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD030', @ProductName='Fairy Dish Soap 500ml',    @UnitPrice=85.00,  @StockQuantity=35, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD031', @ProductName='Toilet Paper 10 Rolls',    @UnitPrice=150.00, @StockQuantity=50, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD032', @ProductName='Tissue Box 200 Sheets',    @UnitPrice=60.00,  @StockQuantity=55, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD033', @ProductName='Candle Pack 12pc',         @UnitPrice=45.00,  @StockQuantity=60, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD034', @ProductName='Garbage Bags 20pc',        @UnitPrice=55.00,  @StockQuantity=40, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD035', @ProductName='Floor Cleaner 1L',         @UnitPrice=95.00,  @StockQuantity=25, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD036', @ProductName='Mosquito Coil 10pc',       @UnitPrice=40.00,  @StockQuantity=45, @CategoryId=3;
EXEC dbo.sp_AddProduct @ProductCode='PRD037', @ProductName='Dove Soap 135g',           @UnitPrice=55.00,  @StockQuantity=60, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD038', @ProductName='Lux Soap 125g',            @UnitPrice=45.00,  @StockQuantity=70, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD039', @ProductName='Vaseline Lotion 400ml',    @UnitPrice=185.00, @StockQuantity=30, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD040', @ProductName='Head & Shoulders 400ml',   @UnitPrice=240.00, @StockQuantity=25, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD041', @ProductName='Colgate Toothbrush',       @UnitPrice=40.00,  @StockQuantity=50, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD042', @ProductName='Deodorant Roll-On 50ml',   @UnitPrice=120.00, @StockQuantity=30, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD043', @ProductName='Sanitary Pads 8pc',        @UnitPrice=65.00,  @StockQuantity=40, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD044', @ProductName='Razors 5pc',               @UnitPrice=55.00,  @StockQuantity=35, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD045', @ProductName='Cotton Buds 100pc',        @UnitPrice=35.00,  @StockQuantity=45, @CategoryId=4;
EXEC dbo.sp_AddProduct @ProductCode='PRD046', @ProductName='Lip Balm',                 @UnitPrice=50.00,  @StockQuantity=40, @CategoryId=4;───
EXEC dbo.sp_AddProduct @ProductCode='PRD047', @ProductName='Lema Fresh Milk 1L',       @UnitPrice=75.00,  @StockQuantity=40, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD048', @ProductName='Mama Fresh Yogurt 500g',   @UnitPrice=90.00,  @StockQuantity=30, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD049', @ProductName='Eggs Tray 30pc',           @UnitPrice=360.00, @StockQuantity=20, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD050', @ProductName='Eggs 6pc',                 @UnitPrice=75.00,  @StockQuantity=35, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD051', @ProductName='Butter 250g',              @UnitPrice=180.00, @StockQuantity=20, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD052', @ProductName='Ayib (Cottage Cheese) 250g',@UnitPrice=95.00, @StockQuantity=15, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD053', @ProductName='Powdered Milk 400g',       @UnitPrice=210.00, @StockQuantity=25, @CategoryId=5;
EXEC dbo.sp_AddProduct @ProductCode='PRD054', @ProductName='White Bread Loaf',         @UnitPrice=40.00,  @StockQuantity=30, @CategoryId=6;
EXEC dbo.sp_AddProduct @ProductCode='PRD055', @ProductName='Whole Wheat Bread Loaf',   @UnitPrice=55.00,  @StockQuantity=25, @CategoryId=6;
EXEC dbo.sp_AddProduct @ProductCode='PRD056', @ProductName='Biscuit Pack 200g',        @UnitPrice=35.00,  @StockQuantity=50, @CategoryId=6;
EXEC dbo.sp_AddProduct @ProductCode='PRD057', @ProductName='Croissant 2pc',            @UnitPrice=45.00,  @StockQuantity=20, @CategoryId=6;
EXEC dbo.sp_AddProduct @ProductCode='PRD058', @ProductName='Injera 10pc',              @UnitPrice=80.00,  @StockQuantity=25, @CategoryId=6;
EXEC dbo.sp_AddProduct @ProductCode='PRD059', @ProductName='Lay"s Chips 28g',         @UnitPrice=30.00,  @StockQuantity=80, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD060', @ProductName='Pringles Original 40g',    @UnitPrice=75.00,  @StockQuantity=40, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD061', @ProductName='Roasted Peanuts 200g',     @UnitPrice=55.00,  @StockQuantity=50, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD062', @ProductName='Choco Pie 6pc',            @UnitPrice=90.00,  @StockQuantity=35, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD063', @ProductName='Candy Mix 100g',           @UnitPrice=25.00,  @StockQuantity=70, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD064', @ProductName='Chocolate Bar 40g',        @UnitPrice=45.00,  @StockQuantity=60, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD065', @ProductName='Popcorn Microwave 85g',    @UnitPrice=65.00,  @StockQuantity=30, @CategoryId=7;
EXEC dbo.sp_AddProduct @ProductCode='PRD066', @ProductName='Sunflower Seeds 150g',     @UnitPrice=35.00,  @StockQuantity=45, @CategoryId=7;

EXEC dbo.sp_AddProduct @ProductCode='PRD067', @ProductName='Berbere Spice 200g',       @UnitPrice=85.00,  @StockQuantity=40, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD068', @ProductName='Mitmita Spice 100g',       @UnitPrice=70.00,  @StockQuantity=35, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD069', @ProductName='Turmeric Powder 100g',     @UnitPrice=50.00,  @StockQuantity=40, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD070', @ProductName='Black Pepper 50g',         @UnitPrice=60.00,  @StockQuantity=35, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD071', @ProductName='Ketchup 340g',             @UnitPrice=75.00,  @StockQuantity=30, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD072', @ProductName='Mayonnaise 250g',          @UnitPrice=95.00,  @StockQuantity=25, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD073', @ProductName='Soy Sauce 150ml',          @UnitPrice=65.00,  @StockQuantity=20, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD074', @ProductName='White Vinegar 500ml',      @UnitPrice=45.00,  @StockQuantity=30, @CategoryId=8;
EXEC dbo.sp_AddProduct @ProductCode='PRD075', @ProductName='Niter Kibbeh 250g',        @UnitPrice=130.00, @StockQuantity=20, @CategoryId=8;

EXEC dbo.sp_AddProduct @ProductCode='PRD076', @ProductName='Teff Flour 2kg',           @UnitPrice=130.00, @StockQuantity=30, @CategoryId=9;
EXEC dbo.sp_AddProduct @ProductCode='PRD077', @ProductName='Barley (Gabs) 1kg',        @UnitPrice=65.00,  @StockQuantity=35, @CategoryId=9;
EXEC dbo.sp_AddProduct @ProductCode='PRD078', @ProductName='Sorghum 1kg',              @UnitPrice=60.00,  @StockQuantity=30, @CategoryId=9;
EXEC dbo.sp_AddProduct @ProductCode='PRD079', @ProductName='Split Peas (Kik) 1kg',     @UnitPrice=80.00,  @StockQuantity=35, @CategoryId=9;
EXEC dbo.sp_AddProduct @ProductCode='PRD080', @ProductName='Black Beans 1kg',          @UnitPrice=95.00,  @StockQuantity=25, @CategoryId=9;
EXEC dbo.sp_AddProduct @ProductCode='PRD081', @ProductName='White Haricot Beans 1kg',  @UnitPrice=85.00,  @StockQuantity=30, @CategoryId=9;
EXEC dbo.sp_AddProduct @ProductCode='PRD082', @ProductName='Oats 500g',                @UnitPrice=90.00,  @StockQuantity=25, @CategoryId=9;

EXEC dbo.sp_AddProduct @ProductCode='PRD083', @ProductName='Beef Sausage 200g',        @UnitPrice=180.00, @StockQuantity=20, @CategoryId=10;
EXEC dbo.sp_AddProduct @ProductCode='PRD084', @ProductName='Chicken Frankfurter 200g', @UnitPrice=155.00, @StockQuantity=20, @CategoryId=10;
EXEC dbo.sp_AddProduct @ProductCode='PRD085', @ProductName='Corned Beef Can 340g',     @UnitPrice=210.00, @StockQuantity=15, @CategoryId=10;
EXEC dbo.sp_AddProduct @ProductCode='PRD086', @ProductName='Canned Sardine 125g',      @UnitPrice=75.00,  @StockQuantity=30, @CategoryId=10;

EXEC dbo.sp_AddProduct @ProductCode='PRD087', @ProductName='Ice Cream Cup 100ml',      @UnitPrice=45.00,  @StockQuantity=30, @CategoryId=11;
EXEC dbo.sp_AddProduct @ProductCode='PRD088', @ProductName='Frozen Chips 500g',        @UnitPrice=120.00, @StockQuantity=20, @CategoryId=11;
EXEC dbo.sp_AddProduct @ProductCode='PRD089', @ProductName='Frozen Peas 500g',         @UnitPrice=95.00,  @StockQuantity=15, @CategoryId=11;

EXEC dbo.sp_AddProduct @ProductCode='PRD090', @ProductName='Diapers Size 3 – 20pc',    @UnitPrice=280.00, @StockQuantity=20, @CategoryId=12;
EXEC dbo.sp_AddProduct @ProductCode='PRD091', @ProductName='Diapers Size 4 – 18pc',    @UnitPrice=295.00, @StockQuantity=20, @CategoryId=12;
EXEC dbo.sp_AddProduct @ProductCode='PRD092', @ProductName='Baby Wipes 80pc',          @UnitPrice=120.00, @StockQuantity=25, @CategoryId=12;
EXEC dbo.sp_AddProduct @ProductCode='PRD093', @ProductName='Cerelac Wheat 250g',       @UnitPrice=210.00, @StockQuantity=15, @CategoryId=12;
EXEC dbo.sp_AddProduct @ProductCode='PRD094', @ProductName='Baby Shampoo 200ml',       @UnitPrice=130.00, @StockQuantity=20, @CategoryId=12;
EXEC dbo.sp_AddProduct @ProductCode='PRD095', @ProductName='Baby Powder 200g',         @UnitPrice=110.00, @StockQuantity=20, @CategoryId=12;
GO

INSERT INTO dbo.Customer (FullName, Phone)
VALUES
    ('Selam Tesfaye',   '0911000004'),
    ('Abebe Girma',     '0911000005'),
    ('Hiwot Alemu',     '0911000006'),
    ('Yonas Tadesse',   '0911000007'),
    ('Feven Mulugeta',  '0911000008');
    ('Meron Abebe', '0911000001'),
    ('Kebede Alemu', '0911000002'),
    ('Walk-in Customer', NULL);
GO

DECLARE @SaleId1 INT;
DECLARE @CustomerId1 INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000001');
DECLARE @CashierId1 INT = (SELECT UserId FROM dbo.AppUser WHERE Username = 'cashier1');
EXEC dbo.sp_CreateSale @CustomerId = @CustomerId1, @CashierId = @CashierId1, @PaymentAmount = 320.00, @SaleId = @SaleId1 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId = @SaleId1, @ProductCode = 'PRD001', @Quantity = 2;
EXEC dbo.sp_AddSaleItem @SaleId = @SaleId1, @ProductCode = 'PRD003', @Quantity = 3;
EXEC dbo.sp_FinalizeSale @SaleId = @SaleId1, @PaymentAmount = 320.00;
GO

DECLARE @SaleId2 INT;
DECLARE @CustomerId2 INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000002');
DECLARE @CashierId2 INT = (SELECT UserId FROM dbo.AppUser WHERE Username = 'cashier2');
EXEC dbo.sp_CreateSale @CustomerId = @CustomerId2, @CashierId = @CashierId2, @PaymentAmount = 255.00, @SaleId = @SaleId2 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId = @SaleId2, @ProductCode = 'PRD004', @Quantity = 1;
EXEC dbo.sp_AddSaleItem @SaleId = @SaleId2, @ProductCode = 'PRD005', @Quantity = 2;
EXEC dbo.sp_AddSaleItem @SaleId = @SaleId2, @ProductCode = 'PRD006', @Quantity = 1;
EXEC dbo.sp_FinalizeSale @SaleId = @SaleId2, @PaymentAmount = 255.00;
GO

DECLARE @SaleId3   INT;
DECLARE @CId3      INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000004');
DECLARE @CashId1   INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier1');
EXEC dbo.sp_CreateSale @CustomerId=@CId3, @CashierId=@CashId1, @PaymentAmount=500.00, @SaleId=@SaleId3 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId3, @ProductCode='PRD007', @Quantity=2;  -- Wheat Flour x2  = 220
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId3, @ProductCode='PRD008', @Quantity=1;  -- Sugar x1        =  70
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId3, @ProductCode='PRD067', @Quantity=1;  -- Berbere x1      =  85
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId3, @ProductCode='PRD003', @Quantity=2;  -- Water x2        =  50
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId3, @PaymentAmount=500.00;
GO

DECLARE @SaleId4   INT;
DECLARE @CId4      INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000005');
DECLARE @CashId2   INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier2');
EXEC dbo.sp_CreateSale @CustomerId=@CId4, @CashierId=@CashId2, @PaymentAmount=700.00, @SaleId=@SaleId4 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId4, @ProductCode='PRD002', @Quantity=2;  -- Cooking Oil x2  = 420
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId4, @ProductCode='PRD009', @Quantity=2;  -- Salt x2         =  50
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId4, @ProductCode='PRD027', @Quantity=1;  -- Omo Detergent   = 130
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId4, @PaymentAmount=700.00;
GO

DECLARE @SaleId5   INT;
DECLARE @CId5      INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000006');
DECLARE @CashId3   INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier3');
EXEC dbo.sp_CreateSale @CustomerId=@CId5, @CashierId=@CashId3, @PaymentAmount=400.00, @SaleId=@SaleId5 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId5, @ProductCode='PRD047', @Quantity=2;  -- Fresh Milk x2   = 150
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId5, @ProductCode='PRD049', @Quantity=1;  -- Eggs Tray       = 360
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId5, @PaymentAmount=520.00;
GO

DECLARE @SaleId6   INT;
DECLARE @CId6      INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000007');
DECLARE @CashId4   INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier4');
EXEC dbo.sp_CreateSale @CustomerId=@CId6, @CashierId=@CashId4, @PaymentAmount=1000.00, @SaleId=@SaleId6 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId6, @ProductCode='PRD090', @Quantity=1;  -- Diapers Size3   = 280
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId6, @ProductCode='PRD092', @Quantity=1;  -- Baby Wipes      = 120
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId6, @ProductCode='PRD093', @Quantity=1;  -- Cerelac         = 210
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId6, @ProductCode='PRD094', @Quantity=1;  -- Baby Shampoo    = 130
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId6, @PaymentAmount=1000.00;
GO

DECLARE @SaleId7   INT;
DECLARE @CId7      INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000008');
DECLARE @CashId1b  INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier1');
EXEC dbo.sp_CreateSale @CustomerId=@CId7, @CashierId=@CashId1b, @PaymentAmount=2000.00, @SaleId=@SaleId7 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD001', @Quantity=3;  -- Rice x3         = 285
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD002', @Quantity=2;  -- Cooking Oil x2  = 420
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD008', @Quantity=2;  -- Sugar x2        = 140
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD011', @Quantity=2;  -- Lentils x2      = 180
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD068', @Quantity=1;  -- Mitmita         =  70
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD067', @Quantity=1;  -- Berbere         =  85
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD075', @Quantity=1;  -- Niter Kibbeh    = 130
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD031', @Quantity=1;  -- Toilet Paper    = 150
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId7, @ProductCode='PRD032', @Quantity=1;  -- Tissue Box      =  60
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId7, @PaymentAmount=2000.00;
GO

DECLARE @SaleId8   INT;
DECLARE @CashId2b  INT = (SELECT UserId FROM dbo.AppUser WHERE Username = 'cashier2');
EXEC dbo.sp_CreateSale @CustomerId=NULL, @CashierId=@CashId2b, @PaymentAmount=300.00, @SaleId=@SaleId8 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId8, @ProductCode='PRD017', @Quantity=3;  -- Coca-Cola x3    =  90
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId8, @ProductCode='PRD059', @Quantity=2;  -- Lays x2         =  60
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId8, @ProductCode='PRD064', @Quantity=2;  -- Chocolate x2    =  90
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId8, @ProductCode='PRD063', @Quantity=1;  -- Candy Mix       =  25
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId8, @PaymentAmount=300.00;
GO

DECLARE @SaleId9   INT;
DECLARE @CId9      INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000001');
DECLARE @CashId3b  INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier3');
EXEC dbo.sp_CreateSale @CustomerId=@CId9, @CashierId=@CashId3b, @PaymentAmount=700.00, @SaleId=@SaleId9 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId9, @ProductCode='PRD039', @Quantity=1;  -- Vaseline        = 185
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId9, @ProductCode='PRD040', @Quantity=1;  -- Head&Shoulders  = 240
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId9, @ProductCode='PRD037', @Quantity=2;  -- Dove Soap x2    = 110
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId9, @ProductCode='PRD006', @Quantity=1;  -- Toothpaste      =  60
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId9, @ProductCode='PRD041', @Quantity=1;  -- Toothbrush      =  40
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId9, @PaymentAmount=700.00;
GO

DECLARE @SaleId10  INT;
DECLARE @CId10     INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000002');
DECLARE @CashId4b  INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier4');
EXEC dbo.sp_CreateSale @CustomerId=@CId10, @CashierId=@CashId4b, @PaymentAmount=500.00, @SaleId=@SaleId10 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId10, @ProductCode='PRD028', @Quantity=1; -- Ariel           =  95
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId10, @ProductCode='PRD029', @Quantity=1; -- Jik Bleach      =  75
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId10, @ProductCode='PRD030', @Quantity=1; -- Fairy Soap       =  85
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId10, @ProductCode='PRD035', @Quantity=1; -- Floor Cleaner   =  95
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId10, @ProductCode='PRD034', @Quantity=1; -- Garbage Bags    =  55
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId10, @PaymentAmount=500.00;
GO

DECLARE @SaleId11  INT;
DECLARE @CashId1c  INT = (SELECT UserId FROM dbo.AppUser WHERE Username = 'cashier1');
EXEC dbo.sp_CreateSale @CustomerId=NULL, @CashierId=@CashId1c, @PaymentAmount=400.00, @SaleId=@SaleId11 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId11, @ProductCode='PRD024', @Quantity=1; -- Abyssinia Coffee = 220
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId11, @ProductCode='PRD054', @Quantity=2; -- White Bread x2  =  80
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId11, @ProductCode='PRD047', @Quantity=1; -- Fresh Milk      =  75
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId11, @PaymentAmount=400.00;
GO

DECLARE @SaleId12  INT;
DECLARE @CId12     INT = (SELECT CustomerId FROM dbo.Customer WHERE Phone = '0911000004');
DECLARE @CashId3c  INT = (SELECT UserId     FROM dbo.AppUser  WHERE Username = 'cashier3');
EXEC dbo.sp_CreateSale @CustomerId=@CId12, @CashierId=@CashId3c, @PaymentAmount=800.00, @SaleId=@SaleId12 OUTPUT;
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId12, @ProductCode='PRD076', @Quantity=2; -- Teff Flour x2   = 260
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId12, @ProductCode='PRD079', @Quantity=2; -- Split Peas x2   = 160
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId12, @ProductCode='PRD081', @Quantity=2; -- White Beans x2  = 170
EXEC dbo.sp_AddSaleItem @SaleId=@SaleId12, @ProductCode='PRD077', @Quantity=1; -- Barley          =  65
EXEC dbo.sp_FinalizeSale @SaleId=@SaleId12, @PaymentAmount=800.00;
GO
