package model;

import java.sql.Timestamp;

/**
 * Maps to the "notifications" table.
 *
 * recipientType is one of USER / AGENT / ADMIN. For ADMIN notifications,
 * recipientId is stored as 0 and ignored (they are broadcast to every admin).
 */
public class Notification {

    private int notificationId;
    private String recipientType;
    private int recipientId;
    private Integer orderId;   // nullable - not every notification is order-related
    private String title;
    private String message;
    private boolean read;
    private Timestamp createdAt;

    // Optional display-only field populated by JOIN queries (not a DB column)
    private String orderNumber;

    public Notification() {
    }

    public Notification(String recipientType, int recipientId, Integer orderId, String title, String message) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.orderId = orderId;
        this.title = title;
        this.message = message;
        this.read = false;
    }

    public int getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public int getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(int recipientId) {
        this.recipientId = recipientId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }
}
