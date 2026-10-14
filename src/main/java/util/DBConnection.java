package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central JDBC connection factory.
 *
 * Reads DB_URL / DB_USER / DB_PASSWORD from environment variables so the
 * same WAR can run against different databases (local dev vs a server)
 * without recompiling. If the env vars are not set, it falls back to a
 * local MySQL instance with database "fuel_delivery_db".
 */
public class DBConnection {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/fuel_delivery_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root@123";

    private static final String URL = envOrDefault("DB_URL", DEFAULT_URL);
    private static final String USER = envOrDefault("DB_USER", DEFAULT_USER);
    private static final String PASSWORD = envOrDefault("DB_PASSWORD", DEFAULT_PASSWORD);

    static {
        try {
            // Explicit load keeps this working even on older servlet containers
            // that don't run JDBC 4 auto-registration from META-INF/services.
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("MySQL JDBC driver not found on classpath: " + e.getMessage());
        }
    }

    private DBConnection() {
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
