package dao;

import model.Payment;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Data access object for the "payments" table.
 * Each fuel_order has at most one payment row (enforced by a UNIQUE
 * constraint on order_id in the schema).
 */
public class PaymentDAO {

    /**
     * Records a payment for an order and generates a transaction_id.
     * Call this right after placeOrder() in the checkout flow.
     *
     * @return the generated payment_id, or -1 if the insert failed
     */
    public int recordPayment(Payment payment) throws SQLException {
        String sql = "INSERT INTO payments (order_id, transaction_id, payment_method, amount, payment_status) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, payment.getOrderId());
            ps.setString(2, generateTransactionId());
            ps.setString(3, payment.getPaymentMethod());
            ps.setBigDecimal(4, payment.getAmount());
            ps.setString(5, payment.getPaymentStatus());

            int rows = ps.executeUpdate();
            if (rows == 0) {
                return -1;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return -1;
    }

    private String generateTransactionId() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    public Payment getPaymentByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM payments WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Updates payment_status (e.g. PENDING -> PAID after a gateway callback,
     * or PAID -> REFUNDED on cancellation). Keep fuel_orders.payment_status
     * in sync by also calling FuelOrderDAO with the same status if needed.
     */
    public boolean updatePaymentStatus(int paymentId, String status) throws SQLException {
        String sql = "UPDATE payments SET payment_status = ? WHERE payment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, paymentId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Sum of all successfully collected payments - the admin dashboard's revenue figure. */
    public BigDecimal getTotalRevenue() throws SQLException {
        String sql = "SELECT IFNULL(SUM(amount), 0) AS total_revenue FROM payments WHERE payment_status = 'PAID'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBigDecimal("total_revenue");
            }
        }
        return BigDecimal.ZERO;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setTransactionId(rs.getString("transaction_id"));
        p.setPaymentMethod(rs.getString("payment_method"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setPaymentStatus(rs.getString("payment_status"));
        p.setPaymentDate(rs.getTimestamp("payment_date"));
        return p;
    }
}
