package servlet;

import dao.FeedbackDAO;
import dao.FuelOrderDAO;
import model.Feedback;
import model.FuelOrder;
import util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * Handles customer feedback/ratings on delivered orders.
 *   GET  ?action=check&orderId=..  -> {"hasFeedback": true/false}
 *   POST (submit feedback)         -> creates a feedback row, only for the
 *                                      order's own customer and only once
 *                                      the order is DELIVERED.
 */
@WebServlet("/feedback")
public class FeedbackServlet extends HttpServlet {

    private final FeedbackDAO feedbackdao = new FeedbackDAO();
    private final FuelOrderDAO orderdao = new FuelOrderDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        String action = request.getParameter("action");

        if (!"check".equals(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            return;
        }

        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            boolean hasFeedback = feedbackdao.hasFeedback(orderId);
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("hasFeedback", hasFeedback)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        } catch (SQLException e) {
            throw new ServletException("Database error while checking feedback", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }
        int userId = (int) session.getAttribute("userId");

        response.setContentType("application/json;charset=UTF-8");

        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            int rating = Integer.parseInt(request.getParameter("rating"));
            String comments = request.getParameter("comments");

            FuelOrder order = orderdao.getOrderById(orderId);
            if (order == null || order.getUserId() != userId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your order");
                return;
            }
            if (!"DELIVERED".equals(order.getOrderStatus())) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Order not yet delivered");
                return;
            }
            if (feedbackdao.hasFeedback(orderId)) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "Feedback already submitted");
                return;
            }

            Feedback feedback = new Feedback(orderId, userId, rating, comments);
            int feedbackId = feedbackdao.submitFeedback(feedback);

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("success", feedbackId != -1),
                        JsonUtil.kv("feedbackId", feedbackId)
                ));
            }

        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid numeric input");
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Database error while submitting feedback", e);
        }
    }
}
