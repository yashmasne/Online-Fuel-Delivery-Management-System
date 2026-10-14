package dao;

import model.ContactMessage;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the "contact_messages" table.
 * Backs the Home Page "Get in Touch" form and the admin's message inbox.
 */
public class ContactMessageDAO {

    /**
     * Saves a new contact form submission.
     *
     * @return the generated message_id, or -1 if the insert failed
     */
    public int submitMessage(ContactMessage msg) throws SQLException {
        String sql = "INSERT INTO contact_messages (full_name, email, message, status) VALUES (?, ?, ?, 'NEW')";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, msg.getFullName());
            ps.setString(2, msg.getEmail());
            ps.setString(3, msg.getMessage());

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

    public ContactMessage getMessageById(int messageId) throws SQLException {
        String sql = "SELECT * FROM contact_messages WHERE message_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /** All messages, most recent first - admin inbox view. */
    public List<ContactMessage> getAllMessages() throws SQLException {
        String sql = "SELECT * FROM contact_messages ORDER BY submitted_at DESC";
        List<ContactMessage> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Messages the admin hasn't opened yet - feeds an "unread" badge/count. */
    public List<ContactMessage> getMessagesByStatus(String status) throws SQLException {
        String sql = "SELECT * FROM contact_messages WHERE status = ? ORDER BY submitted_at DESC";
        List<ContactMessage> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int getUnreadCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS unread FROM contact_messages WHERE status = 'NEW'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("unread");
            }
        }
        return 0;
    }

    /** Move a message through NEW -> READ -> RESPONDED as the admin handles it. */
    public boolean updateStatus(int messageId, String status) throws SQLException {
        String sql = "UPDATE contact_messages SET status = ? WHERE message_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, messageId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteMessage(int messageId) throws SQLException {
        String sql = "DELETE FROM contact_messages WHERE message_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, messageId);
            return ps.executeUpdate() > 0;
        }
    }

    private ContactMessage mapRow(ResultSet rs) throws SQLException {
        ContactMessage msg = new ContactMessage();
        msg.setMessageId(rs.getInt("message_id"));
        msg.setFullName(rs.getString("full_name"));
        msg.setEmail(rs.getString("email"));
        msg.setMessage(rs.getString("message"));
        msg.setStatus(rs.getString("status"));
        msg.setSubmittedAt(rs.getTimestamp("submitted_at"));
        return msg;
    }
}
