package dao;

import model.User;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the "users" table.
 * Covers registration, login, profile lookups, and admin user management.
 */
public class UserDAO {

    /**
     * Registers a new customer account. Role is always forced to USER here -
     * admin accounts should be created directly in the database (see the
     * sample INSERT in db/schema.sql), never through public signup.
     *
     * @return the generated user_id, or -1 if the insert failed
     */
    public int registerUser(User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, phone, password, address, city, pincode, role, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'USER', 'ACTIVE')";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPassword()); // store a hashed password, never plain text
            ps.setString(5, user.getAddress());
            ps.setString(6, user.getCity());
            ps.setString(7, user.getPincode());

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

    /**
     * Registers a new ADMIN account. Only ever call this from an endpoint that
     * has already verified the caller is logged in as an existing admin
     * (see AdminManagementServlet#writeAddAdmin) - never expose this on the
     * public signup form.
     *
     * @return the generated user_id, or -1 if the insert failed
     */
    public int registerAdmin(User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, phone, password, address, city, pincode, role, status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ADMIN', 'ACTIVE')";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPassword()); // store a hashed password, never plain text
            ps.setString(5, user.getAddress());
            ps.setString(6, user.getCity());
            ps.setString(7, user.getPincode());

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

    /** All ADMIN-role accounts, for the "manage team" screen. */
    public List<User> getAllAdmins() throws SQLException {
        String sql = "SELECT * FROM users WHERE role = 'ADMIN' ORDER BY created_at DESC";
        List<User> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Renames an existing admin (or any user). Used by the "manage team" screen
     * to put a real person's name on the admin account instead of a generic one.
     */
    public boolean updateFullName(int userId, String fullName) throws SQLException {
        String sql = "UPDATE users SET full_name = ? WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Checks whether an email is already registered - call before registerUser()
     * to give a friendly "email already in use" message instead of relying on
     * the DB's unique-constraint SQLException.
     */
    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE email = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Validates login credentials and returns the matching user, or null if
     * no active account matches. Compare the password using your hashing
     * library (e.g. BCrypt.checkpw(rawPassword, user.getPassword())) in the
     * servlet after fetching the user - do not compare plain text in production.
     */
    public User login(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ? AND status = 'ACTIVE'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public User getUserById(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /** All USER-role accounts, for the admin's customer management screen. */
    public List<User> getAllCustomers() throws SQLException {
        String sql = "SELECT * FROM users WHERE role = 'USER' ORDER BY created_at DESC";
        List<User> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public boolean updateProfile(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, phone = ?, address = ?, city = ?, pincode = ? "
                + "WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getPhone());
            ps.setString(3, user.getAddress());
            ps.setString(4, user.getCity());
            ps.setString(5, user.getPincode());
            ps.setInt(6, user.getUserId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Activate/deactivate an account instead of deleting it. */
    public boolean updateStatus(int userId, String status) throws SQLException {
        String sql = "UPDATE users SET status = ? WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setPassword(rs.getString("password"));
        user.setAddress(rs.getString("address"));
        user.setCity(rs.getString("city"));
        user.setPincode(rs.getString("pincode"));
        user.setRole(rs.getString("role"));
        user.setStatus(rs.getString("status"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        return user;
    }
}
