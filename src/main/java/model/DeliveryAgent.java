package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Maps to the "delivery_agents" table.
 */
public class DeliveryAgent {

    private int agentId;
    private String fullName;
    private String email;
    private String phone;
    private String password;
    private String vehicleNumber;
    private String vehicleType;
    private String availabilityStatus; // AVAILABLE, BUSY
    private Timestamp createdAt;

    // The agent's last shared GPS position (from their own dashboard), used to find
    // the nearest available agent to a new order. Null until they've shared once.
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Timestamp locationUpdatedAt;

    public DeliveryAgent() {
    }

    public DeliveryAgent(int agentId, String fullName, String email, String phone, String password,
                          String vehicleNumber, String vehicleType, String availabilityStatus,
                          Timestamp createdAt) {
        this.agentId = agentId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.availabilityStatus = availabilityStatus;
        this.createdAt = createdAt;
    }

    // Convenience constructor for onboarding a new agent
    public DeliveryAgent(String fullName, String email, String phone, String password,
                          String vehicleNumber, String vehicleType) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.availabilityStatus = "AVAILABLE";
    }

    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(int agentId) {
        this.agentId = agentId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(String availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public Timestamp getLocationUpdatedAt() {
        return locationUpdatedAt;
    }

    public void setLocationUpdatedAt(Timestamp locationUpdatedAt) {
        this.locationUpdatedAt = locationUpdatedAt;
    }

    public boolean hasKnownLocation() {
        return latitude != null && longitude != null;
    }

    public boolean isAvailable() {
        return "AVAILABLE".equalsIgnoreCase(availabilityStatus);
    }

    @Override
    public String toString() {
        return "DeliveryAgent{" +
                "agentId=" + agentId +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", vehicleNumber='" + vehicleNumber + '\'' +
                ", vehicleType='" + vehicleType + '\'' +
                ", availabilityStatus='" + availabilityStatus + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
