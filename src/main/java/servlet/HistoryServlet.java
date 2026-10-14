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
import java.util.ArrayList;
import java.util.List;

/**
 * Backs History_Profile.html - returns the logged-in customer's full order
 * history and the feedback they've left, combined into one JSON payload.
 */
@WebServlet("/history")
public class HistoryServlet extends HttpServlet {

    private final FuelOrderDAO orderdao = new FuelOrderDAO();
    private final FeedbackDAO feedbackdao = new FeedbackDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }
        int userId = (int) session.getAttribute("userId");
        response.setContentType("application/json;charset=UTF-8");
        try {
            List<FuelOrder> orders = orderdao.getOrdersByUser(userId);
            List<Feedback> feedbackList = feedbackdao.getFeedbackByUser(userId);

            List<String> orderJson = new ArrayList<>();
            for (FuelOrder o : orders) {
                orderJson.add(FuelOrderServlet.orderToJson(o));
            }
            List<String> feedbackJson = new ArrayList<>();
            for (Feedback f : feedbackList) {
                feedbackJson.add(JsonUtil.object(
                        JsonUtil.kv("feedbackId", f.getFeedbackId()),
                        JsonUtil.kv("orderId", f.getOrderId()),
                        JsonUtil.kv("rating", f.getRating()),
                        JsonUtil.kv("comments", f.getComments()),
                        JsonUtil.kv("feedbackDate", f.getFeedbackDate())
                ));
            }
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        "\"orders\":" + JsonUtil.array(orderJson),
                        "\"feedback\":" + JsonUtil.array(feedbackJson)
                ));
            }
        } catch (SQLException e) {
            throw new ServletException("Database error while loading history", e);
        }
    }
}
