package model;

import java.sql.Timestamp;

/**
 * Maps to the "feedback" table.
 * Each order can have exactly one feedback record (order_id is UNIQUE in the DB).
 */
public class Feedback {

    private int feedbackId;
    private int orderId;
    private int userId;
    private int rating;       // 1 to 5 (enforced by CHECK constraint in DB)
    private String comments;
    private Timestamp feedbackDate;

    public Feedback() {
    }

    public Feedback(int feedbackId, int orderId, int userId, int rating,
                     String comments, Timestamp feedbackDate) {
        this.feedbackId = feedbackId;
        this.orderId = orderId;
        this.userId = userId;
        this.rating = rating;
        this.comments = comments;
        this.feedbackDate = feedbackDate;
    }

    // Convenience constructor for submitting new feedback
    public Feedback(int orderId, int userId, int rating, String comments) {
        this.orderId = orderId;
        this.userId = userId;
        this.rating = rating;
        this.comments = comments;
    }

    public int getFeedbackId() {
        return feedbackId;
    }

    public void setFeedbackId(int feedbackId) {
        this.feedbackId = feedbackId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getRating() {
        return rating;
    }

    /**
     * Sets the rating. Validates range before it ever reaches the DB
     * so bad input fails fast in the servlet layer, not at the JDBC call.
     */
    public void setRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.rating = rating;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Timestamp getFeedbackDate() {
        return feedbackDate;
    }

    public void setFeedbackDate(Timestamp feedbackDate) {
        this.feedbackDate = feedbackDate;
    }

    @Override
    public String toString() {
        return "Feedback{" +
                "feedbackId=" + feedbackId +
                ", orderId=" + orderId +
                ", userId=" + userId +
                ", rating=" + rating +
                ", comments='" + comments + '\'' +
                ", feedbackDate=" + feedbackDate +
                '}';
    }
}
