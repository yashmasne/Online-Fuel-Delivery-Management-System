package servlet;

import dao.NotificationDAO;
import model.Notification;
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
 * Backs the notification bell shown on User_Dashboard.html, Order_Track.html
 * and Delivery_Agent_Dashboard.html.
 *
 * The recipient (USER / AGENT / ADMIN) is resolved from whichever session
 * attribute is present, so the same endpoint works for every role without
 * the caller needing to say who they are.
 *
 *   GET  ?action=list         -> { unreadCount, notifications: [...] } for the logged-in caller
 *   POST ?action=markRead     -> marks one notification read (notificationId param)
 *   POST ?action=markAllRead  -> marks every notification for this caller read
 */
@WebServlet("/notifications")
public class NotificationServlet extends HttpServlet {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        response.setContentType("application/json;charset=UTF-8");

        String[] recipient = resolveRecipient(request);
        if (recipient == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }

        try {
            if (!"list".equals(action)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
                return;
            }
            String recipientType = recipient[0];
            int recipientId = Integer.parseInt(recipient[1]);

            List<Notification> notifications = notificationDAO.getRecent(recipientType, recipientId, 30);
            int unreadCount = notificationDAO.getUnreadCount(recipientType, recipientId);

            List<String> jsonObjects = new ArrayList<>();
            for (Notification n : notifications) {
                jsonObjects.add(notificationToJson(n));
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("unreadCount", unreadCount),
                        "\"notifications\":" + JsonUtil.array(jsonObjects)
                ));
            }
        } catch (SQLException e) {
            throw new ServletException("Database error in NotificationServlet", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        response.setContentType("application/json;charset=UTF-8");

        String[] recipient = resolveRecipient(request);
        if (recipient == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return;
        }
        String recipientType = recipient[0];
        int recipientId = Integer.parseInt(recipient[1]);

        try {
            switch (action == null ? "" : action) {
                case "markRead": {
                    int notificationId = Integer.parseInt(request.getParameter("notificationId"));
                    boolean updated = notificationDAO.markRead(notificationId, recipientType, recipientId);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
                    }
                    break;
                }
                case "markAllRead": {
                    boolean updated = notificationDAO.markAllRead(recipientType, recipientId);
                    try (PrintWriter out = response.getWriter()) {
                        out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
                    }
                    break;
                }
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameter");
        } catch (SQLException e) {
            throw new ServletException("Database error in NotificationServlet", e);
        }
    }

    /** Returns {recipientType, recipientId} based on whichever login session is active, or null. */
    private String[] resolveRecipient(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        if (session.getAttribute("agentId") != null) {
            return new String[]{"AGENT", String.valueOf(session.getAttribute("agentId"))};
        }
        if (session.getAttribute("adminId") != null) {
            return new String[]{"ADMIN", String.valueOf(session.getAttribute("adminId"))};
        }
        if (session.getAttribute("userId") != null) {
            return new String[]{"USER", String.valueOf(session.getAttribute("userId"))};
        }
        return null;
    }

    private static String notificationToJson(Notification n) {
        return JsonUtil.object(
                JsonUtil.kv("notificationId", n.getNotificationId()),
                JsonUtil.kv("orderId", n.getOrderId()),
                JsonUtil.kv("title", n.getTitle()),
                JsonUtil.kv("message", n.getMessage()),
                JsonUtil.kv("isRead", n.isRead()),
                JsonUtil.kv("createdAt", n.getCreatedAt())
        );
    }
}
