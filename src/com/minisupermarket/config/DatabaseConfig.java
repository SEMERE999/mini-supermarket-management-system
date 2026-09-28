package com.minisupermarket.config;

public class DatabaseConfig {
    private final String jdbcUrlOverride;
    private final String host;
    private final String instanceName;
    private final String port;
    private final String databaseName;
    private final String username;
    private final String password;
    private final String encrypt;
    private final String trustServerCertificate;

    public DatabaseConfig() {
        this.jdbcUrlOverride = getEnv("MSSQL_JDBC_URL");
        this.host = getEnvOrDefault("MSSQL_HOST", "localhost");
        this.instanceName = getEnv("MSSQL_INSTANCE");
        this.port = getEnvOrDefault("MSSQL_PORT", "1433");
        this.databaseName = getEnvOrDefault("MSSQL_DB", "MiniSupermarketDB");
        this.username = getEnvOrDefault("MSSQL_USER", "sa");
        this.password = getEnvOrDefault("MSSQL_PASSWORD", "YourStrongPassword123");
        this.encrypt = getEnvOrDefault("MSSQL_ENCRYPT", "true");
        this.trustServerCertificate = getEnvOrDefault("MSSQL_TRUST_SERVER_CERTIFICATE", "true");
    }

    public String buildJdbcUrl() {
        if (jdbcUrlOverride != null) {
            return jdbcUrlOverride;
        }

        if (instanceName != null) {
            return String.format(
                "jdbc:sqlserver://%s;instanceName=%s;databaseName=%s;encrypt=%s;trustServerCertificate=%s;",
                host,
                instanceName,
                databaseName,
                encrypt,
                trustServerCertificate
            );
        }

        return String.format(
            "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=%s;trustServerCertificate=%s;",
            host,
            port,
            databaseName,
            encrypt,
            trustServerCertificate
        );
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    private String getEnvOrDefault(String key, String defaultValue) {
        String value = getEnv(key);
        return value == null ? defaultValue : value;
    }

    private String getEnv(String key) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? null : value;
    }
}
