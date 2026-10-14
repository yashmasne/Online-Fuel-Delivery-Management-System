package model;

import java.sql.Timestamp;

/**
 * Maps to the "users" table.
 * Represents both ADMIN and USER (customer) accounts - distinguished by
 * the "role" column, not a separate table.
 */
public class User {

    private int userId;
    private String fullName;
    private String email;
    private String phone;
    private String password;
    private String address;
    private String city;
    private String pincode;
    private String role;      // ADMIN, USER
    private String status;    // ACTIVE, INACTIVE
    private Timestamp createdAt;

    public User() {
    }

    public User(int userId, String fullName, String email, String phone, String password,
                String address, String city, String pincode, String role, String status,
                Timestamp createdAt) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.address = address;
        this.city = city;
        this.pincode = pincode;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Convenience constructor for registration (before user_id/created_at exist)
    public User(String fullName, String email, String phone, String password,
                String address, String city, String pincode) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.address = address;
        this.city = city;
        this.pincode = pincode;
        this.role = "USER";
        this.status = "ACTIVE";
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", city='" + city + '\'' +
                ", role='" + role + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
