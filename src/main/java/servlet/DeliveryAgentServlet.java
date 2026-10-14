package servlet;

import dao.DeliveryAgentDAO;
import dao.FuelOrderDAO;
import dao.NotificationDAO;
import model.DeliveryAgent;
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
 * Backs Delivery_Agent_Dashboard.html.
 *   POST ?action=login              -> agent login, starts session
 *   GET  ?action=myOrders           -> JSON list of orders assigned to this agent
 *   POST ?action=startDelivery      -> moves an ASSIGNED order to OUT_FOR_DELIVERY and
 *                                       notifies the customer that live tracking has begun
 *   POST ?action=markDelivered      -> marks an order DELIVERED, frees up the agent
 *   POST ?action=updateAvailability -> manually toggle AVAILABLE/BUSY
 */
@WebServlet("/deliveryAgent")
public class DeliveryAgentServlet extends HttpServlet {

    private final DeliveryAgentDAO agentDAO = new DeliveryAgentDAO();
    private final FuelOrderDAO orderDAO = new FuelOrderDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        response.setContentType("application/json;charset=UTF-8");

        if (!"myOrders".equals(action) && !"availableOrders".equals(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            return;
        }

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        try {
            List<FuelOrder> orders = "availableOrders".equals(action)
                    ? orderDAO.getUnassignedOrders()
                    : orderDAO.getOrdersByAgent(agentId);
            List<String> jsonObjects = new ArrayList<>();
            for (FuelOrder o : orders) {
                jsonObjects.add(FuelOrderServlet.orderToJson(o));
            }
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.array(jsonObjects));
            }
        } catch (SQLException e) {
            throw new ServletException("Database error while loading agent orders", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        try {
            switch (action == null ? "" : action) {
                case "login":
                    handleLogin(request, response);
                    break;
                case "register":
                    handleRegister(request, response);
                    break;
                case "startDelivery":
                    handleStartDelivery(request, response);
                    break;
                case "markDelivered":
                    handleMarkDelivered(request, response);
                    break;
                case "updateAvailability":
                    handleUpdateAvailability(request, response);
                    break;
                case "updateLocation":
                    handleUpdateLocation(request, response);
                    break;
                case "acceptOrder":
                    handleAcceptOrder(request, response);
                    break;
                case "cancelOrder":
                    handleCancelUnclaimedOrder(request, response);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (SQLException e) {
            throw new ServletException("Database error in DeliveryAgentServlet", e);
        }
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            response.sendRedirect("Auth_Pages.html?mode=agent&error=missing");
            return;
        }

        DeliveryAgent agent = agentDAO.login(email);

        // NOTE: comparing plain text here for simplicity - use BCrypt.checkpw in production.
        if (agent == null || !agent.getPassword().equals(password)) {
            response.sendRedirect("Auth_Pages.html?mode=agent&error=invalid");
            return;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("agentId", agent.getAgentId());
        session.setAttribute("agentName", agent.getFullName());
        session.setAttribute("agentEmail", agent.getEmail());

        response.sendRedirect("Delivery_Agent_Dashboard.html");
    }

    private void handleRegister(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");
        String vehicleNumber = request.getParameter("vehicleNumber");
        String vehicleType = request.getParameter("vehicleType");

        if (fullName == null || email == null || phone == null || password == null
                || fullName.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            response.sendRedirect("Auth_Pages.html?mode=agentRegister&error=missing");
            return;
        }

        if (agentDAO.emailExists(email)) {
            response.sendRedirect("Auth_Pages.html?mode=agentRegister&error=exists");
            return;
        }

        // NOTE: store a hashed password in production, e.g. BCrypt.hashpw(password, BCrypt.gensalt())
        DeliveryAgent newAgent = new DeliveryAgent(fullName, email, phone, password, vehicleNumber, vehicleType);
        int agentId = agentDAO.registerAgent(newAgent);

        if (agentId == -1) {
            response.sendRedirect("Auth_Pages.html?mode=agentRegister&error=failed");
            return;
        }

        response.sendRedirect("Auth_Pages.html?mode=agent&registered=true");
    }

    private void handleStartDelivery(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));

            FuelOrder order = orderDAO.getOrderById(orderId);
            if (order == null || order.getAgentId() == null || order.getAgentId() != agentId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Order not assigned to you");
                return;
            }
            if (!"ASSIGNED".equals(order.getOrderStatus())) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Order is not ready to start delivery");
                return;
            }

            boolean updated = orderDAO.updateOrderStatus(orderId, "OUT_FOR_DELIVERY");

            if (updated) {
                try {
                    notificationDAO.notify("USER", order.getUserId(), orderId, "Out for delivery",
                            "Your order " + order.getOrderNumber() + " is out for delivery. Live tracking has started!");
                } catch (SQLException ignored) {
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        }
    }

    private void handleMarkDelivered(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));

            FuelOrder order = orderDAO.getOrderById(orderId);
            if (order == null || order.getAgentId() == null || order.getAgentId() != agentId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Order not assigned to you");
                return;
            }

            boolean statusUpdated = orderDAO.updateOrderStatus(orderId, "DELIVERED");
            boolean availabilityUpdated = agentDAO.updateAvailability(agentId, "AVAILABLE");

            if (statusUpdated) {
                try {
                    notificationDAO.notify("USER", order.getUserId(), orderId, "Order delivered",
                            "Your order " + order.getOrderNumber() + " has been delivered. Thank you for choosing FuelExpress!");
                } catch (SQLException ignored) {
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", statusUpdated && availabilityUpdated)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        }
    }

    private void handleUpdateAvailability(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        String status = request.getParameter("status"); // AVAILABLE or BUSY
        boolean updated = agentDAO.updateAvailability(agentId, status);

        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.object(JsonUtil.kv("success", updated), JsonUtil.kv("status", status)));
        }
    }

    /**
     * Lets an agent claim an unassigned order themselves straight from their dashboard's
     * "Available Orders" list, instead of waiting for an admin to assign one to them.
     */
    private void handleAcceptOrder(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            DeliveryAgent agent = agentDAO.getAgentById(agentId);
            if (agent == null || !agent.isAvailable()) {
                try (PrintWriter out = response.getWriter()) {
                    out.print(JsonUtil.object(JsonUtil.kv("success", false),
                            JsonUtil.kv("message", "You're marked BUSY - set yourself Available before accepting an order.")));
                }
                return;
            }

            boolean claimed = orderDAO.claimOrderIfUnassigned(orderId, agentId);

            if (claimed) {
                try {
                    FuelOrder order = orderDAO.getOrderById(orderId);
                    if (order != null) {
                        notificationDAO.notify("USER", order.getUserId(), orderId, "Agent assigned",
                                agent.getFullName() + " has accepted your order " + order.getOrderNumber()
                                        + ". You can track it live now.");
                    }
                } catch (SQLException ignored) {
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("success", claimed),
                        JsonUtil.kv("message", claimed ? null : "Someone else already accepted this order.")
                ));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        }
    }

    /**
     * Lets an agent cancel an order straight from the "Available Orders" list - only while it's
     * still unclaimed (PENDING/CONFIRMED, agent_id IS NULL). Once an order is accepted by anyone
     * it disappears from that list entirely, so this can never cancel a delivery already in
     * progress. Mirrors FuelOrderServlet's customer-facing cancelOrder, just triggered from the
     * agent side and without the "must be your own order" check that doesn't apply here.
     */
    private void handleCancelUnclaimedOrder(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            FuelOrder order = orderDAO.getOrderById(orderId);

            if (order == null || order.getAgentId() != null
                    || !("PENDING".equals(order.getOrderStatus()) || "CONFIRMED".equals(order.getOrderStatus()))) {
                try (PrintWriter out = response.getWriter()) {
                    out.print(JsonUtil.object(JsonUtil.kv("success", false),
                            JsonUtil.kv("message", "This order was already claimed or is no longer cancellable.")));
                }
                return;
            }

            boolean cancelled = orderDAO.updateOrderStatus(orderId, "CANCELLED");
            if (cancelled) {
                try {
                    notificationDAO.notify("USER", order.getUserId(), orderId, "Order cancelled",
                            "Your order " + order.getOrderNumber() + " was cancelled - please contact support "
                                    + "or place a new order if this wasn't expected.");
                } catch (SQLException ignored) {
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", cancelled)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        }
    }

    private void handleUpdateLocation(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer agentId = requireLoggedInAgent(request, response);
        if (agentId == null) {
            return;
        }

        response.setContentType("application/json;charset=UTF-8");
        try {
            java.math.BigDecimal latitude = new java.math.BigDecimal(request.getParameter("latitude"));
            java.math.BigDecimal longitude = new java.math.BigDecimal(request.getParameter("longitude"));
            boolean updated = agentDAO.updateLocation(agentId, latitude, longitude);
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
            }
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid latitude/longitude");
        }
    }

    private Integer requireLoggedInAgent(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("agentId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Agent login required");
            return null;
        }
        return (Integer) session.getAttribute("agentId");
    }
}
