package dao;

import model.Feedback;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the "feedback" table.
 * Each order can have at most one feedback row (UNIQUE order_id in the schema) -
 * only allow feedback submission once order_status = 'DELIVERED' in the servlet.
 */
public class FeedbackDAO {

    /**
     * Submits feedback for a delivered order.
     *
     * @return the generated feedback_id, or -1 if the insert failed
     */
    public int submitFeedback(Feedback feedback) throws SQLException {
        String sql = "INSERT INTO feedback (order_id, user_id, rating, comments) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, feedback.getOrderId());
            ps.setInt(2, feedback.getUserId());
            ps.setInt(3, feedback.getRating());
            ps.setString(4, feedback.getComments());

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

    public Feedback getFeedbackByOrderId(int orderId) throws SQLException {
        String sql = "SELECT * FROM feedback WHERE order_id = ?";
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

    /** Quick check before showing the "rate this order" form. */
    public boolean hasFeedback(int orderId) throws SQLException {
        String sql = "SELECT feedback_id FROM feedback WHERE order_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** All feedback left by a given customer. */
    public List<Feedback> getFeedbackByUser(int userId) throws SQLException {
        String sql = "SELECT * FROM feedback WHERE user_id = ? ORDER BY feedback_date DESC";
        List<Feedback> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /** All feedback, most recent first - admin review screen. */
    public List<Feedback> getAllFeedback() throws SQLException {
        String sql = "SELECT * FROM feedback ORDER BY feedback_date DESC";
        List<Feedback> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Overall average rating across all feedback, for the admin dashboard. */
    public double getAverageRating() throws SQLException {
        String sql = "SELECT IFNULL(AVG(rating), 0) AS avg_rating FROM feedback";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getDouble("avg_rating");
            }
        }
        return 0.0;
    }

    private Feedback mapRow(ResultSet rs) throws SQLException {
        Feedback f = new Feedback();
        f.setFeedbackId(rs.getInt("feedback_id"));
        f.setOrderId(rs.getInt("order_id"));
        f.setUserId(rs.getInt("user_id"));
        f.setRating(rs.getInt("rating"));
        f.setComments(rs.getString("comments"));
        f.setFeedbackDate(rs.getTimestamp("feedback_date"));
        return f;
    }
}
