package dao;

import model.Notification;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs the in-app notification centre used by customers, delivery agents
 * and admins (see NotificationServlet).
 *
 * recipientType is one of USER / AGENT / ADMIN. ADMIN notifications are
 * broadcast: they are stored with recipient_id = 0 and every admin session
 * sees the same feed, since only one row is needed per event instead of one
 * per admin account.
 */
public class NotificationDAO {

    /** Creates a notification. Safe to call from inside a larger try block; callers may
     *  choose to swallow SQLException here so a notification failure never blocks
     *  the primary action (placing an order, assigning an agent, etc.). */
    public int notify(String recipientType, int recipientId, Integer orderId, String title, String message)
            throws SQLException {
        String sql = "INSERT INTO notifications (recipient_type, recipient_id, order_id, title, message) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, recipientType);
            ps.setInt(2, recipientId);
            if (orderId == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, orderId);
            }
            ps.setString(4, title);
            ps.setString(5, message);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    /** Most recent notifications for a recipient (ADMIN ignores recipientId - broadcast feed). */
    public List<Notification> getRecent(String recipientType, int recipientId, int limit) throws SQLException {
        String sql = "ADMIN".equals(recipientType)
                ? "SELECT * FROM notifications WHERE recipient_type = 'ADMIN' ORDER BY created_at DESC LIMIT ?"
                : "SELECT * FROM notifications WHERE recipient_type = ? AND recipient_id = ? ORDER BY created_at DESC LIMIT ?";

        List<Notification> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if ("ADMIN".equals(recipientType)) {
                ps.setInt(1, limit);
            } else {
                ps.setString(1, recipientType);
                ps.setInt(2, recipientId);
                ps.setInt(3, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int getUnreadCount(String recipientType, int recipientId) throws SQLException {
        String sql = "ADMIN".equals(recipientType)
                ? "SELECT COUNT(*) FROM notifications WHERE recipient_type = 'ADMIN' AND is_read = 0"
                : "SELECT COUNT(*) FROM notifications WHERE recipient_type = ? AND recipient_id = ? AND is_read = 0";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (!"ADMIN".equals(recipientType)) {
                ps.setString(1, recipientType);
                ps.setInt(2, recipientId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /** Marks a single notification read, scoped to its owner so one recipient can't mark another's. */
    public boolean markRead(int notificationId, String recipientType, int recipientId) throws SQLException {
        String sql = "ADMIN".equals(recipientType)
                ? "UPDATE notifications SET is_read = 1 WHERE notification_id = ? AND recipient_type = 'ADMIN'"
                : "UPDATE notifications SET is_read = 1 WHERE notification_id = ? AND recipient_type = ? AND recipient_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            if (!"ADMIN".equals(recipientType)) {
                ps.setString(2, recipientType);
                ps.setInt(3, recipientId);
            }
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markAllRead(String recipientType, int recipientId) throws SQLException {
        String sql = "ADMIN".equals(recipientType)
                ? "UPDATE notifications SET is_read = 1 WHERE recipient_type = 'ADMIN' AND is_read = 0"
                : "UPDATE notifications SET is_read = 1 WHERE recipient_type = ? AND recipient_id = ? AND is_read = 0";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (!"ADMIN".equals(recipientType)) {
                ps.setString(1, recipientType);
                ps.setInt(2, recipientId);
            }
            ps.executeUpdate();
            return true;
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getInt("notification_id"));
        n.setRecipientType(rs.getString("recipient_type"));
        n.setRecipientId(rs.getInt("recipient_id"));
        int orderId = rs.getInt("order_id");
        n.setOrderId(rs.wasNull() ? null : orderId);
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getTimestamp("created_at"));
        return n;
    }
}
