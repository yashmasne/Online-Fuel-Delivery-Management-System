package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Maps to the "fuel_orders" table.
 *
 * Note: agentId is nullable in the DB (order may not yet be assigned to a
 * delivery agent), so use the Integer wrapper class, not int, when reading
 * a possibly-NULL agent_id from the ResultSet.
 */
public class FuelOrder {

    private int orderId;
    private String orderNumber;
    private int userId;
    private Integer agentId;      // nullable - order may be unassigned
    private int fuelId;
    private String vehicleNumber;
    private String vehicleType;
    private BigDecimal quantity;
    private BigDecimal pricePerLitre;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String paymentMethod;   // CASH, CARD, UPI, WALLET
    private String paymentStatus;   // PENDING, PAID, FAILED, REFUNDED
    private String orderStatus;     // PENDING, CONFIRMED, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED
    private Timestamp orderDate;
    private Timestamp deliveryDate; // nullable until delivered

    // Optional display-only fields populated by JOIN queries (not DB columns)
    private String customerName;
    private String agentName;
    private String fuelName;

    public FuelOrder() {
    }

    public FuelOrder(int orderId, String orderNumber, int userId, Integer agentId, int fuelId,
                      String vehicleNumber, String vehicleType, BigDecimal quantity,
                      BigDecimal pricePerLitre, BigDecimal totalAmount, String deliveryAddress,
                      BigDecimal latitude, BigDecimal longitude, String paymentMethod,
                      String paymentStatus, String orderStatus, Timestamp orderDate,
                      Timestamp deliveryDate) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.agentId = agentId;
        this.fuelId = fuelId;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.quantity = quantity;
        this.pricePerLitre = pricePerLitre;
        this.totalAmount = totalAmount;
        this.deliveryAddress = deliveryAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.orderStatus = orderStatus;
        this.orderDate = orderDate;
        this.deliveryDate = deliveryDate;
    }

    // Convenience constructor for placing a new order (before order_id/order_date exist)
    public FuelOrder(String orderNumber, int userId, int fuelId, String vehicleNumber,
                      String vehicleType, BigDecimal quantity, BigDecimal pricePerLitre,
                      BigDecimal totalAmount, String deliveryAddress, BigDecimal latitude,
                      BigDecimal longitude, String paymentMethod) {
        this.orderNumber = orderNumber;
        this.userId = userId;
        this.fuelId = fuelId;
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
        this.quantity = quantity;
        this.pricePerLitre = pricePerLitre;
        this.totalAmount = totalAmount;
        this.deliveryAddress = deliveryAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = "PENDING";
        this.orderStatus = "PENDING";
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Integer getAgentId() {
        return agentId;
    }

    public void setAgentId(Integer agentId) {
        this.agentId = agentId;
    }

    public int getFuelId() {
        return fuelId;
    }

    public void setFuelId(int fuelId) {
        this.fuelId = fuelId;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPricePerLitre() {
        return pricePerLitre;
    }

    public void setPricePerLitre(BigDecimal pricePerLitre) {
        this.pricePerLitre = pricePerLitre;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Timestamp getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Timestamp orderDate) {
        this.orderDate = orderDate;
    }

    public Timestamp getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(Timestamp deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getFuelName() {
        return fuelName;
    }

    public void setFuelName(String fuelName) {
        this.fuelName = fuelName;
    }

    @Override
    public String toString() {
        return "FuelOrder{" +
                "orderId=" + orderId +
                ", orderNumber='" + orderNumber + '\'' +
                ", userId=" + userId +
                ", agentId=" + agentId +
                ", fuelId=" + fuelId +
                ", quantity=" + quantity +
                ", totalAmount=" + totalAmount +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", orderStatus='" + orderStatus + '\'' +
                ", orderDate=" + orderDate +
                ", deliveryDate=" + deliveryDate +
                '}';
    }
}
