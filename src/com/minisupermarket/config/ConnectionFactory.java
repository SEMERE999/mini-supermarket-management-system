package com.minisupermarket.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionFactory {
    private static final DatabaseConfig CONFIG = new DatabaseConfig();

    private ConnectionFactory() {
    }

    public static Connection createConnection() throws SQLException {
        return DriverManager.getConnection(
            CONFIG.buildJdbcUrl(),
            CONFIG.getUsername(),
            CONFIG.getPassword()
        );
    }
}
