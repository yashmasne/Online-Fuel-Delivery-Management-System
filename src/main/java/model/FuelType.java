package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Maps to the "fuel_types" table (Petrol, Diesel, CNG, LPG, etc).
 * This class was referenced by dao.FuelTypeDAO but was missing from the
 * original project - added here so the project compiles.
 */
public class FuelType {

    private int fuelId;
    private String fuelName;
    private BigDecimal pricePerLitre;
    private String description;

    // Set automatically when LiveFuelPriceFetcher writes a freshly-fetched price;
    // stays null for fuel types (like LPG) that are only ever priced by an admin.
    private Timestamp priceUpdatedAt;

    public FuelType() {
    }

    public FuelType(int fuelId, String fuelName, BigDecimal pricePerLitre, String description) {
        this.fuelId = fuelId;
        this.fuelName = fuelName;
        this.pricePerLitre = pricePerLitre;
        this.description = description;
    }

    // Convenience constructor for adding a new fuel type (before fuel_id exists)
    public FuelType(String fuelName, BigDecimal pricePerLitre, String description) {
        this.fuelName = fuelName;
        this.pricePerLitre = pricePerLitre;
        this.description = description;
    }

    public int getFuelId() {
        return fuelId;
    }

    public void setFuelId(int fuelId) {
        this.fuelId = fuelId;
    }

    public String getFuelName() {
        return fuelName;
    }

    public void setFuelName(String fuelName) {
        this.fuelName = fuelName;
    }

    public BigDecimal getPricePerLitre() {
        return pricePerLitre;
    }

    public void setPricePerLitre(BigDecimal pricePerLitre) {
        this.pricePerLitre = pricePerLitre;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getPriceUpdatedAt() {
        return priceUpdatedAt;
    }

    public void setPriceUpdatedAt(Timestamp priceUpdatedAt) {
        this.priceUpdatedAt = priceUpdatedAt;
    }

    @Override
    public String toString() {
        return "FuelType{" +
                "fuelId=" + fuelId +
                ", fuelName='" + fuelName + '\'' +
                ", pricePerLitre=" + pricePerLitre +
                ", description='" + description + '\'' +
                '}';
    }
}
