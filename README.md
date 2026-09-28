# Mini Supermarket Management System

A desktop and console-based management application built in **Java** and backed by **Microsoft SQL Server**. The system handles inventory tracking, sales processing, cashier/manager user roles, and reporting[cite: 1, 2].

   Key Features

*   Multi-Role User Authentication:** Manager and Cashier roles with password hashing (`SHA2_256`)[cite: 1, 2].
*   Inventory Management:** Category and product creation, restocking triggers, and stock availability tracking[cite: 1, 2].
*   Sales & Checkout Processing:** Transaction handling with automated line-item calculations and inventory deductions[cite: 1, 2].
*   Database Triggers & Stored Procedures:** Enforced relational constraints, transaction validation, and automated totals via MS SQL Server[cite: 2].
*   Dual Interface:** Supports both Console UI and Swing Desktop GUI[cite: 1].

  Tech Stack

*   Language:** Java (JDK 17+)[cite: 1]
*   Database:** Microsoft SQL Server[cite: 2]
*   Database Access:** JDBC (Data Access Object / Repository Pattern)[cite: 1]
*   Version Control:** Git & GitHub[cite: 1]

   Database Setup

1. Open SQL Server Management Studio (SSMS).
2. Open and execute the script located at `db/database_setup.sql`[cite: 2].
3. Ensure the `MiniSupermarketDB` is created and populated with sample data[cite: 2].