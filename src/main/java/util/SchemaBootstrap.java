package util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs once when the app starts up and creates anything the newer features
 * need but an older database might not have yet: the "notifications" table,
 * and the "price_updated_at" column on "fuel_types".
 *
 * This exists because those two things previously had to be added by hand
 * (running sql/notifications_schema.sql and sql/live_fuel_price_schema.sql),
 * and a missed migration made the whole fuel-ordering flow fail with no
 * obvious cause - FuelTypeDAO reads price_updated_at on every single query,
 * so a missing column broke fuel-type lookups, and therefore Place Order,
 * everywhere. Checking and fixing this automatically on startup means a
 * fresh deploy against an older database heals itself instead of silently
 * breaking. It never drops or rewrites existing data - only adds what's
 * missing, and only if it's actually missing.
 */
@WebListener
public class SchemaBootstrap implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try (Connection con = DBConnection.getConnection()) {
            ensureFuelTypesPriceUpdatedAtColumn(con);
            ensureNotificationsTable(con);
            ensureDeliveryAgentLocationColumns(con);
        } catch (SQLException e) {
            // Don't stop the app from starting just because this check failed (e.g. the DB
            // isn't reachable yet) - log it clearly so it's easy to spot in server logs.
            System.err.println("SchemaBootstrap: could not verify/update database schema on startup - " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to clean up.
    }

    private void ensureFuelTypesPriceUpdatedAtColumn(Connection con) throws SQLException {
        if (columnExists(con, "fuel_types", "price_updated_at")) {
            return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(
                    "ALTER TABLE fuel_types "
                            + "ADD COLUMN price_updated_at TIMESTAMP NULL DEFAULT NULL");
            System.out.println("SchemaBootstrap: added missing fuel_types.price_updated_at column.");
        }
    }

    private void ensureNotificationsTable(Connection con) throws SQLException {
        if (tableExists(con, "notifications")) {
            return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE notifications ("
                            + "notification_id INT AUTO_INCREMENT PRIMARY KEY,"
                            + "recipient_type VARCHAR(10) NOT NULL,"
                            + "recipient_id   INT NOT NULL,"
                            + "order_id INT NULL,"
                            + "title    VARCHAR(150) NOT NULL,"
                            + "message  VARCHAR(500) NOT NULL,"
                            + "is_read  TINYINT(1) NOT NULL DEFAULT 0,"
                            + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                            + "CONSTRAINT fk_notifications_order FOREIGN KEY (order_id) REFERENCES fuel_orders(order_id) ON DELETE CASCADE,"
                            + "INDEX idx_notifications_recipient (recipient_type, recipient_id, is_read),"
                            + "INDEX idx_notifications_order (order_id)"
                            + ")");
            System.out.println("SchemaBootstrap: created missing notifications table.");
        }
    }

    private void ensureDeliveryAgentLocationColumns(Connection con) throws SQLException {
        if (!columnExists(con, "delivery_agents", "latitude")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE delivery_agents ADD COLUMN latitude DECIMAL(10,7) NULL DEFAULT NULL");
            }
        }
        if (!columnExists(con, "delivery_agents", "longitude")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE delivery_agents ADD COLUMN longitude DECIMAL(10,7) NULL DEFAULT NULL");
            }
        }
        if (!columnExists(con, "delivery_agents", "location_updated_at")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE delivery_agents ADD COLUMN location_updated_at TIMESTAMP NULL DEFAULT NULL");
            }
            System.out.println("SchemaBootstrap: added missing delivery_agents location columns.");
        }
    }

    private boolean columnExists(Connection con, String table, String column) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getColumns(con.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private boolean tableExists(Connection con, String table) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
