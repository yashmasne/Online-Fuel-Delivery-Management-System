package model;

import java.sql.Timestamp;

/**
 * Maps to the "contact_messages" table.
 * Represents a submission from the Home Page "Get in Touch" form.
 */
public class ContactMessage {

    private int messageId;
    private String fullName;
    private String email;
    private String message;
    private String status; // NEW, READ, RESPONDED
    private Timestamp submittedAt;

    public ContactMessage() {
    }

    public ContactMessage(int messageId, String fullName, String email, String message,
                           String status, Timestamp submittedAt) {
        this.messageId = messageId;
        this.fullName = fullName;
        this.email = email;
        this.message = message;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    // Convenience constructor for a new submission from the contact form
    public ContactMessage(String fullName, String email, String message) {
        this.fullName = fullName;
        this.email = email;
        this.message = message;
        this.status = "NEW";
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Timestamp submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String toString() {
        return "ContactMessage{" +
                "messageId=" + messageId +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", status='" + status + '\'' +
                ", submittedAt=" + submittedAt +
                '}';
    }
}
