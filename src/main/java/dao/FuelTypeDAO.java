package dao;

import model.FuelType;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the "fuel_types" table.
 * Small lookup/catalog table (Petrol, Diesel, CNG, LPG) - feeds the fuel
 * dropdown on the order form and lets the admin update prices centrally.
 */
public class FuelTypeDAO {

    public List<FuelType> getAllFuelTypes() throws SQLException {
        String sql = "SELECT * FROM fuel_types ORDER BY fuel_name";
        List<FuelType> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public FuelType getFuelTypeById(int fuelId) throws SQLException {
        String sql = "SELECT * FROM fuel_types WHERE fuel_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, fuelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public FuelType getFuelTypeByName(String fuelName) throws SQLException {
        String sql = "SELECT * FROM fuel_types WHERE fuel_name = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fuelName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /** Admin action: add a new fuel type (e.g. CNG, Premium Petrol). */
    public int addFuelType(FuelType fuelType) throws SQLException {
        String sql = "INSERT INTO fuel_types (fuel_name, price_per_litre, description) VALUES (?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fuelType.getFuelName());
            ps.setBigDecimal(2, fuelType.getPricePerLitre());
            ps.setString(3, fuelType.getDescription());

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
     * Admin action: update the price per litre for a fuel type. Existing
     * orders keep their own stored price_per_litre snapshot, so past order
     * totals are unaffected by this change.
     */
    public boolean updatePrice(int fuelId, BigDecimal newPrice) throws SQLException {
        String sql = "UPDATE fuel_types SET price_per_litre = ? WHERE fuel_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setInt(2, fuelId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Used by LiveFuelPriceFetcher after it pulls a fresh price from the public source page.
     * Distinct from updatePrice() so the two "who changed this price" stories - an admin
     * typing a number in, vs. the daily auto-refresh - stay easy to tell apart later.
     */
    public boolean updateLivePrice(int fuelId, BigDecimal newPrice, Timestamp fetchedAt) throws SQLException {
        String sql = "UPDATE fuel_types SET price_per_litre = ?, price_updated_at = ? WHERE fuel_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setTimestamp(2, fetchedAt);
            ps.setInt(3, fuelId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateFuelType(FuelType fuelType) throws SQLException {
        String sql = "UPDATE fuel_types SET fuel_name = ?, price_per_litre = ?, description = ? WHERE fuel_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fuelType.getFuelName());
            ps.setBigDecimal(2, fuelType.getPricePerLitre());
            ps.setString(3, fuelType.getDescription());
            ps.setInt(4, fuelType.getFuelId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a fuel type. Will fail with a foreign key violation if any
     * fuel_orders reference it (ON DELETE RESTRICT in the schema) - catch
     * SQLException in the servlet and show "cannot delete: fuel type in use".
     */
    public boolean deleteFuelType(int fuelId) throws SQLException {
        String sql = "DELETE FROM fuel_types WHERE fuel_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, fuelId);
            return ps.executeUpdate() > 0;
        }
    }

    private FuelType mapRow(ResultSet rs) throws SQLException {
        FuelType f = new FuelType();
        f.setFuelId(rs.getInt("fuel_id"));
        f.setFuelName(rs.getString("fuel_name"));
        f.setPricePerLitre(rs.getBigDecimal("price_per_litre"));
        f.setDescription(rs.getString("description"));
        // SchemaBootstrap adds this column automatically on startup, but if that ever
        // can't run (e.g. the DB user lacks ALTER privileges), fall back to "no live
        // price yet" instead of breaking every fuel-type lookup - and therefore every order.
        try {
            f.setPriceUpdatedAt(rs.getTimestamp("price_updated_at"));
        } catch (SQLException columnMissing) {
            f.setPriceUpdatedAt(null);
        }
        return f;
    }
}
