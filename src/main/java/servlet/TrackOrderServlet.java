package servlet;

import dao.FuelOrderDAO;
import model.FuelOrder;

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
 * Backs Order_Track.html - given an order_id, returns its current status,
 * assigned agent, and delivery coordinates as JSON so the page's JS can
 * render/poll a live tracking view.
 */
@WebServlet("/trackOrder")
public class TrackOrderServlet extends HttpServlet {

    private final FuelOrderDAO orderDAO = new FuelOrderDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        String orderIdParam = request.getParameter("orderId");
        if (orderIdParam == null || orderIdParam.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "orderId is required");
            return;
        }
        try {
            int orderId = Integer.parseInt(orderIdParam);
            FuelOrder order = orderDAO.getOrderById(orderId);
            if (order == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Order not found");
                return;
            }
            if (!isAuthorized(request, order)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your order");
                return;
            }
            try (PrintWriter out = response.getWriter()) {
                out.print(FuelOrderServlet.orderToJson(order));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        } catch (SQLException e) {
            throw new ServletException("Database error while tracking order", e);
        }
    }

    /** Allows the order's own customer, an assigned agent, or any logged-in admin. */
    private boolean isAuthorized(HttpServletRequest request, FuelOrder order) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        if (session.getAttribute("adminId") != null) {
            return true;
        }
        Object userId = session.getAttribute("userId");
        if (userId != null && (int) userId == order.getUserId()) {
            return true;
        }
        Object agentId = session.getAttribute("agentId");
        return agentId != null && order.getAgentId() != null && (int) agentId == order.getAgentId();
    }
}
