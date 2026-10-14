package dao;

import model.DeliveryAgent;
import util.DBConnection;
import util.GeoUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the "delivery_agents" table.
 */
public class DeliveryAgentDAO {

    public int registerAgent(DeliveryAgent agent) throws SQLException {
        String sql = "INSERT INTO delivery_agents "
                + "(full_name, email, phone, password, vehicle_number, vehicle_type, availability_status) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE')";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, agent.getFullName());
            ps.setString(2, agent.getEmail());
            ps.setString(3, agent.getPhone());
            ps.setString(4, agent.getPassword());
            ps.setString(5, agent.getVehicleNumber());
            ps.setString(6, agent.getVehicleType());

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

    /** Checks whether an email is already registered as a delivery agent. */
    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT agent_id FROM delivery_agents WHERE email = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public DeliveryAgent login(String email) throws SQLException {
        String sql = "SELECT * FROM delivery_agents WHERE email = ?";
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

    public DeliveryAgent getAgentById(int agentId) throws SQLException {
        String sql = "SELECT * FROM delivery_agents WHERE agent_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, agentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<DeliveryAgent> getAllAgents() throws SQLException {
        String sql = "SELECT * FROM delivery_agents ORDER BY full_name";
        List<DeliveryAgent> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Agents currently free to be assigned a new delivery - feeds the "Assign Agent" dropdown. */
    public List<DeliveryAgent> getAvailableAgents() throws SQLException {
        String sql = "SELECT * FROM delivery_agents WHERE availability_status = 'AVAILABLE' ORDER BY full_name";
        List<DeliveryAgent> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Flip an agent's availability - call with 'BUSY' on assignment and 'AVAILABLE' after delivery. */
    public boolean updateAvailability(int agentId, String availabilityStatus) throws SQLException {
        String sql = "UPDATE delivery_agents SET availability_status = ? WHERE agent_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, availabilityStatus);
            ps.setInt(2, agentId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Called from the agent's own dashboard so their live position is on file for nearest-agent matching. */
    public boolean updateLocation(int agentId, BigDecimal latitude, BigDecimal longitude) throws SQLException {
        String sql = "UPDATE delivery_agents SET latitude = ?, longitude = ?, location_updated_at = ? WHERE agent_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, latitude);
            ps.setBigDecimal(2, longitude);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setInt(4, agentId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Picks the AVAILABLE agent physically closest to the given point, out of the agents who
     * have shared a location at least once. Returns null if no available agent has a known
     * location yet - the order is simply left unassigned for an admin to assign by hand, same
     * as before this feature existed.
     */
    public DeliveryAgent findNearestAvailable(BigDecimal customerLat, BigDecimal customerLng) throws SQLException {
        DeliveryAgent nearest = null;
        double nearestDistanceKm = Double.MAX_VALUE;

        for (DeliveryAgent agent : getAvailableAgents()) {
            if (!agent.hasKnownLocation()) {
                continue;
            }
            double distanceKm = GeoUtils.distanceKm(customerLat, customerLng, agent.getLatitude(), agent.getLongitude());
            if (distanceKm < nearestDistanceKm) {
                nearestDistanceKm = distanceKm;
                nearest = agent;
            }
        }
        return nearest;
    }

    private DeliveryAgent mapRow(ResultSet rs) throws SQLException {
        DeliveryAgent agent = new DeliveryAgent();
        agent.setAgentId(rs.getInt("agent_id"));
        agent.setFullName(rs.getString("full_name"));
        agent.setEmail(rs.getString("email"));
        agent.setPhone(rs.getString("phone"));
        agent.setPassword(rs.getString("password"));
        agent.setVehicleNumber(rs.getString("vehicle_number"));
        agent.setVehicleType(rs.getString("vehicle_type"));
        agent.setAvailabilityStatus(rs.getString("availability_status"));
        agent.setCreatedAt(rs.getTimestamp("created_at"));
        // SchemaBootstrap adds these columns automatically on startup; fall back to "unknown
        // location" instead of breaking every agent lookup in the rare case that didn't run.
        try {
            agent.setLatitude(rs.getBigDecimal("latitude"));
            agent.setLongitude(rs.getBigDecimal("longitude"));
            agent.setLocationUpdatedAt(rs.getTimestamp("location_updated_at"));
        } catch (SQLException columnsMissing) {
            agent.setLatitude(null);
            agent.setLongitude(null);
            agent.setLocationUpdatedAt(null);
        }
        return agent;
    }
}
