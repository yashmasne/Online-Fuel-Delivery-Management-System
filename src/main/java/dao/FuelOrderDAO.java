package dao;

import model.FuelOrder;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FuelOrderDAO {

    public int placeOrder(FuelOrder order) throws SQLException {
        String sql = "INSERT INTO fuel_orders "
                + "(order_number, user_id, fuel_id, vehicle_number, vehicle_type, quantity, price_per_litre, "
                + " total_amount, delivery_address, latitude, longitude, payment_method, payment_status, order_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'PENDING')";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, generateOrderNumber());
            ps.setInt(2, order.getUserId());
            ps.setInt(3, order.getFuelId());
            ps.setString(4, order.getVehicleNumber());
            ps.setString(5, order.getVehicleType());
            ps.setBigDecimal(6, order.getQuantity());
            ps.setBigDecimal(7, order.getPricePerLitre());
            ps.setBigDecimal(8, order.getTotalAmount());
            ps.setString(9, order.getDeliveryAddress());
            ps.setBigDecimal(10, order.getLatitude());
            ps.setBigDecimal(11, order.getLongitude());
            ps.setString(12, order.getPaymentMethod());

            int rows = ps.executeUpdate();
            if (rows == 0) return -1;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    private String generateOrderNumber() {
        return "FE-ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public boolean updateOrderStatus(int orderId, String newStatus) throws SQLException {
        String sql = "UPDATE fuel_orders SET order_status = ?, "
                + "delivery_date = CASE WHEN ? = 'DELIVERED' THEN NOW() ELSE delivery_date END "
                + "WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, newStatus);
            ps.setInt(3, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Updates fuel_orders.payment_status (PENDING/PAID/FAILED/REFUNDED) - kept
     * separate from updateOrderStatus() which controls the delivery workflow
     * (PENDING/CONFIRMED/ASSIGNED/OUT_FOR_DELIVERY/DELIVERED/CANCELLED). The
     * two are different columns with different enums and must not be mixed.
     */
    public boolean updatePaymentStatus(int orderId, String paymentStatus) throws SQLException {
        String sql = "UPDATE fuel_orders SET payment_status = ? WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, paymentStatus);
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean assignAgentToOrder(int orderId, int agentId) throws SQLException {
        String updateOrder = "UPDATE fuel_orders SET agent_id = ?, order_status = 'ASSIGNED' WHERE order_id = ?";
        String updateAgent = "UPDATE delivery_agents SET availability_status = 'BUSY' WHERE agent_id = ?";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps1 = con.prepareStatement(updateOrder)) {
                ps1.setInt(1, agentId);
                ps1.setInt(2, orderId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = con.prepareStatement(updateAgent)) {
                ps2.setInt(1, agentId);
                ps2.executeUpdate();
            }

            con.commit();
            return true;
        } catch (SQLException e) {
            if (con != null) con.rollback();
            throw e;
        } finally {
            if (con != null) {
                con.setAutoCommit(true);
                con.close();
            }
        }
    }

    public FuelOrder getOrderById(int orderId) throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, a.full_name AS agent_name, f.fuel_name "
                + "FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "LEFT JOIN delivery_agents a ON o.agent_id = a.agent_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRowWithJoins(rs);
            }
        }
        return null;
    }

    public List<FuelOrder> getOrdersByUser(int userId) throws SQLException {
        String sql = "SELECT o.*, f.fuel_name FROM fuel_orders o "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.user_id = ? ORDER BY o.order_date DESC";
        List<FuelOrder> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FuelOrder o = mapRow(rs);
                    o.setFuelName(rs.getString("fuel_name"));
                    list.add(o);
                }
            }
        }
        return list;
    }

    public List<FuelOrder> getPendingOrders() throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, f.fuel_name FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.order_status IN ('PENDING','CONFIRMED','ASSIGNED','OUT_FOR_DELIVERY') "
                + "ORDER BY o.order_date ASC";
        return queryOrderListWithJoins(sql);
    }

    /** Orders no agent has been assigned to yet - feeds the agent's own "Available Orders" list,
     *  so an agent can accept a nearby order themselves instead of waiting for an admin. */
    public List<FuelOrder> getUnassignedOrders() throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, f.fuel_name FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.agent_id IS NULL AND o.order_status IN ('PENDING','CONFIRMED') "
                + "ORDER BY o.order_date ASC";
        return queryOrderListWithJoins(sql);
    }

    /**
     * Lets an agent claim an unassigned order themselves. Guarded by "agent_id IS NULL" in the
     * WHERE clause so that if two agents tap Accept on the same order at nearly the same moment,
     * only the first UPDATE actually applies - the second one simply reports false instead of
     * silently double-assigning the order.
     */
    public boolean claimOrderIfUnassigned(int orderId, int agentId) throws SQLException {
        String updateOrder = "UPDATE fuel_orders SET agent_id = ?, order_status = 'ASSIGNED' "
                + "WHERE order_id = ? AND agent_id IS NULL";
        String updateAgent = "UPDATE delivery_agents SET availability_status = 'BUSY' WHERE agent_id = ?";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            boolean claimed;
            try (PreparedStatement ps1 = con.prepareStatement(updateOrder)) {
                ps1.setInt(1, agentId);
                ps1.setInt(2, orderId);
                claimed = ps1.executeUpdate() > 0;
            }
            if (claimed) {
                try (PreparedStatement ps2 = con.prepareStatement(updateAgent)) {
                    ps2.setInt(1, agentId);
                    ps2.executeUpdate();
                }
            }

            con.commit();
            return claimed;
        } catch (SQLException e) {
            if (con != null) {
                con.rollback();
            }
            throw e;
        } finally {
            if (con != null) {
                con.setAutoCommit(true);
                con.close();
            }
        }
    }

    public List<FuelOrder> getCompletedOrders() throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, f.fuel_name FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.order_status = 'DELIVERED' ORDER BY o.delivery_date DESC";
        return queryOrderListWithJoins(sql);
    }

    /** Orders assigned to a specific delivery agent, most recent first. */
    public List<FuelOrder> getOrdersByAgent(int agentId) throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, f.fuel_name FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.agent_id = ? ORDER BY o.order_date DESC";
        List<FuelOrder> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, agentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FuelOrder o = mapRow(rs);
                    o.setCustomerName(rs.getString("customer_name"));
                    o.setFuelName(rs.getString("fuel_name"));
                    list.add(o);
                }
            }
        }
        return list;
    }

    public List<FuelOrder> getTodaysOrders() throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, f.fuel_name FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE DATE(o.order_date) = CURDATE() ORDER BY o.order_date DESC";
        return queryOrderListWithJoins(sql);
    }

    public List<FuelOrder> getAllOrders() throws SQLException {
        String sql = "SELECT o.*, u.full_name AS customer_name, a.full_name AS agent_name, f.fuel_name "
                + "FROM fuel_orders o "
                + "JOIN users u ON o.user_id = u.user_id "
                + "LEFT JOIN delivery_agents a ON o.agent_id = a.agent_id "
                + "JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "ORDER BY o.order_date DESC";
        List<FuelOrder> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowWithJoins(rs));
            }
        }
        return list;
    }

    public Map<String, BigDecimal> getMonthlySales() throws SQLException {
        String sql = "SELECT DATE_FORMAT(order_date, '%Y-%m') AS sales_month, SUM(total_amount) AS total_sales "
                + "FROM fuel_orders WHERE payment_status = 'PAID' "
                + "GROUP BY DATE_FORMAT(order_date, '%Y-%m') ORDER BY sales_month DESC";
        Map<String, BigDecimal> result = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("sales_month"), rs.getBigDecimal("total_sales"));
            }
        }
        return result;
    }

    public List<Map<String, Object>> getFuelSalesReport() throws SQLException {
        String sql = "SELECT f.fuel_name, SUM(o.quantity) AS total_litres, SUM(o.total_amount) AS total_revenue, "
                + "COUNT(o.order_id) AS total_orders "
                + "FROM fuel_orders o JOIN fuel_types f ON o.fuel_id = f.fuel_id "
                + "WHERE o.payment_status = 'PAID' GROUP BY f.fuel_name";
        List<Map<String, Object>> report = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("fuelName", rs.getString("fuel_name"));
                row.put("totalLitres", rs.getBigDecimal("total_litres"));
                row.put("totalRevenue", rs.getBigDecimal("total_revenue"));
                row.put("totalOrders", rs.getInt("total_orders"));
                report.add(row);
            }
        }
        return report;
    }

    public Map<String, Object> getDashboardSummary() throws SQLException {
        String sql = "SELECT "
                + " (SELECT COUNT(*) FROM users WHERE role = 'USER') AS total_customers, "
                + " (SELECT COUNT(*) FROM delivery_agents) AS total_agents, "
                + " (SELECT COUNT(*) FROM fuel_orders) AS total_orders, "
                + " (SELECT COUNT(*) FROM fuel_orders WHERE order_status = 'PENDING') AS pending_orders, "
                + " (SELECT COUNT(*) FROM fuel_orders WHERE order_status = 'DELIVERED') AS delivered_orders, "
                + " (SELECT IFNULL(SUM(amount),0) FROM payments WHERE payment_status = 'PAID') AS total_revenue";

        Map<String, Object> summary = new HashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                summary.put("totalCustomers", rs.getInt("total_customers"));
                summary.put("totalAgents", rs.getInt("total_agents"));
                summary.put("totalOrders", rs.getInt("total_orders"));
                summary.put("pendingOrders", rs.getInt("pending_orders"));
                summary.put("deliveredOrders", rs.getInt("delivered_orders"));
                summary.put("totalRevenue", rs.getBigDecimal("total_revenue"));
            }
        }
        return summary;
    }

    private List<FuelOrder> queryOrderListWithJoins(String sql) throws SQLException {
        List<FuelOrder> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                FuelOrder o = mapRow(rs);
                o.setCustomerName(rs.getString("customer_name"));
                o.setFuelName(rs.getString("fuel_name"));
                list.add(o);
            }
        }
        return list;
    }

    private FuelOrder mapRowWithJoins(ResultSet rs) throws SQLException {
        FuelOrder o = mapRow(rs);
        o.setCustomerName(rs.getString("customer_name"));
        o.setFuelName(rs.getString("fuel_name"));
        try {
            o.setAgentName(rs.getString("agent_name"));
        } catch (SQLException ignored) {}
        return o;
    }

    private FuelOrder mapRow(ResultSet rs) throws SQLException {
        FuelOrder o = new FuelOrder();
        o.setOrderId(rs.getInt("order_id"));
        o.setOrderNumber(rs.getString("order_number"));
        o.setUserId(rs.getInt("user_id"));

        int agentId = rs.getInt("agent_id");
        o.setAgentId(rs.wasNull() ? null : agentId);

        o.setFuelId(rs.getInt("fuel_id"));
        o.setVehicleNumber(rs.getString("vehicle_number"));
        o.setVehicleType(rs.getString("vehicle_type"));
        o.setQuantity(rs.getBigDecimal("quantity"));
        o.setPricePerLitre(rs.getBigDecimal("price_per_litre"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setDeliveryAddress(rs.getString("delivery_address"));
        o.setLatitude(rs.getBigDecimal("latitude"));
        o.setLongitude(rs.getBigDecimal("longitude"));
        o.setPaymentMethod(rs.getString("payment_method"));
        o.setPaymentStatus(rs.getString("payment_status"));
        o.setOrderStatus(rs.getString("order_status"));
        o.setOrderDate(rs.getTimestamp("order_date"));
        o.setDeliveryDate(rs.getTimestamp("delivery_date"));
        return o;
    }
}
