package servlet;

import dao.ContactMessageDAO;
import dao.DeliveryAgentDAO;
import dao.FeedbackDAO;
import dao.FuelOrderDAO;
import dao.FuelTypeDAO;
import dao.NotificationDAO;
import dao.UserDAO;
import model.ContactMessage;
import model.DeliveryAgent;
import model.FuelOrder;
import model.User;
import util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet("/adminManagement")
public class AdminManagementServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();
    private final DeliveryAgentDAO agentDAO = new DeliveryAgentDAO();
    private final FuelOrderDAO orderDAO = new FuelOrderDAO();
    private final FuelTypeDAO fuelTypeDAO = new FuelTypeDAO();
    private final FeedbackDAO feedbackDAO = new FeedbackDAO();
    private final ContactMessageDAO contactMessageDAO = new ContactMessageDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!isAdmin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin login required");
            return;
        }

        String action = request.getParameter("action");
        response.setContentType("application/json;charset=UTF-8");

        try {
            switch (action == null ? "" : action) {
                case "dashboardSummary":  writeDashboardSummary(response); break;
                case "allUsers":          writeAllUsers(response); break;
                case "toggleUserStatus":  writeToggleUserStatus(request, response); break;
                case "allAgents":         writeAllAgents(response); break;
                case "addAgent":          writeAddAgent(request, response); break;
                case "allAdmins":         writeAllAdmins(response); break;
                case "addAdmin":          writeAddAdmin(request, response); break;
                case "renameAdmin":       writeRenameAdmin(request, response); break;
                case "allOrders":         writeOrderList(orderDAO.getAllOrders(), response); break;
                case "pendingOrders":     writeOrderList(orderDAO.getPendingOrders(), response); break;
                case "completedOrders":   writeOrderList(orderDAO.getCompletedOrders(), response); break;
                case "assignAgent":       writeAssignAgent(request, response); break;
                case "updateOrderStatus": writeUpdateOrderStatus(request, response); break;
                case "fuelSalesReport":   writeFuelSalesReport(response); break;
                case "monthlySales":      writeMonthlySales(response); break;
                case "contactMessages":   writeContactMessages(response); break;
                case "updateFuelPrice":   writeUpdateFuelPrice(request, response); break;
                case "allFeedback":       writeAllFeedback(response); break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (SQLException e) {
            throw new ServletException("Database error in AdminManagementServlet", e);
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("adminId") != null;
    }

    private void writeDashboardSummary(HttpServletResponse response) throws SQLException, IOException {
        Map<String, Object> summary = orderDAO.getDashboardSummary();
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.object(
                    JsonUtil.kv("totalCustomers", (Number) summary.get("totalCustomers")),
                    JsonUtil.kv("totalAgents", (Number) summary.get("totalAgents")),
                    JsonUtil.kv("totalOrders", (Number) summary.get("totalOrders")),
                    JsonUtil.kv("pendingOrders", (Number) summary.get("pendingOrders")),
                    JsonUtil.kv("deliveredOrders", (Number) summary.get("deliveredOrders")),
                    JsonUtil.kvDecimal("totalRevenue", (BigDecimal) summary.get("totalRevenue"))
            ));
        }
    }

    private void writeAllUsers(HttpServletResponse response) throws SQLException, IOException {
        List<User> users = userDAO.getAllCustomers();
        List<String> jsonObjects = new ArrayList<>();
        for (User u : users) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("userId", u.getUserId()),
                    JsonUtil.kv("fullName", u.getFullName()),
                    JsonUtil.kv("email", u.getEmail()),
                    JsonUtil.kv("phone", u.getPhone()),
                    JsonUtil.kv("city", u.getCity()),
                    JsonUtil.kv("status", u.getStatus()),
                    JsonUtil.kv("createdAt", u.getCreatedAt())
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeToggleUserStatus(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        try {
            int userId = Integer.parseInt(request.getParameter("userId"));
            String status = request.getParameter("status");
            boolean updated = userDAO.updateStatus(userId, status);
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid userId");
        }
    }

    private void writeAllAgents(HttpServletResponse response) throws SQLException, IOException {
        List<DeliveryAgent> agents = agentDAO.getAllAgents();
        List<String> jsonObjects = new ArrayList<>();
        for (DeliveryAgent a : agents) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("agentId", a.getAgentId()),
                    JsonUtil.kv("fullName", a.getFullName()),
                    JsonUtil.kv("email", a.getEmail()),
                    JsonUtil.kv("phone", a.getPhone()),
                    JsonUtil.kv("vehicleNumber", a.getVehicleNumber()),
                    JsonUtil.kv("vehicleType", a.getVehicleType()),
                    JsonUtil.kv("availabilityStatus", a.getAvailabilityStatus())
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeAddAgent(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");
        String vehicleNumber = request.getParameter("vehicleNumber");
        String vehicleType = request.getParameter("vehicleType");

        if (fullName == null || email == null || password == null || fullName.isBlank() || email.isBlank() || password.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing agent details");
            return;
        }

        DeliveryAgent newAgent = new DeliveryAgent(fullName, email, phone, password, vehicleNumber, vehicleType);
        int agentId = agentDAO.registerAgent(newAgent);

        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.object(
                    JsonUtil.kv("success", agentId != -1),
                    JsonUtil.kv("agentId", agentId)
            ));
        }
    }

    private void writeAllAdmins(HttpServletResponse response) throws SQLException, IOException {
        List<User> admins = userDAO.getAllAdmins();
        List<String> jsonObjects = new ArrayList<>();
        for (User a : admins) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("userId", a.getUserId()),
                    JsonUtil.kv("fullName", a.getFullName()),
                    JsonUtil.kv("email", a.getEmail()),
                    JsonUtil.kv("phone", a.getPhone()),
                    JsonUtil.kv("createdAt", a.getCreatedAt())
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeAddAdmin(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");

        if (fullName == null || email == null || password == null
                || fullName.isBlank() || email.isBlank() || password.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing admin details");
            return;
        }
        if (userDAO.emailExists(email)) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Email already in use");
            return;
        }

        User newAdmin = new User();
        newAdmin.setFullName(fullName);
        newAdmin.setEmail(email);
        newAdmin.setPhone(phone);
        newAdmin.setPassword(password); // hash before storing in production
        int userId = userDAO.registerAdmin(newAdmin);

        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.object(
                    JsonUtil.kv("success", userId != -1),
                    JsonUtil.kv("userId", userId)
            ));
        }
    }

    private void writeRenameAdmin(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        try {
            int userId = Integer.parseInt(request.getParameter("userId"));
            String fullName = request.getParameter("fullName");
            if (fullName == null || fullName.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing fullName");
                return;
            }
            boolean updated = userDAO.updateFullName(userId, fullName);
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid userId");
        }
    }

    private void writeOrderList(List<FuelOrder> orders, HttpServletResponse response) throws IOException {
        List<String> jsonObjects = new ArrayList<>();
        for (FuelOrder o : orders) {
            jsonObjects.add(FuelOrderServlet.orderToJson(o));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeAssignAgent(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            int agentId = Integer.parseInt(request.getParameter("agentId"));
            boolean assigned = orderDAO.assignAgentToOrder(orderId, agentId);

            if (assigned) {
                // Tell the agent they have a new delivery, and tell the customer their
                // order can now be tracked live. Never let a notification failure mask
                // the assignment that already succeeded.
                try {
                    FuelOrder order = orderDAO.getOrderById(orderId);
                    if (order != null) {
                        notificationDAO.notify("AGENT", agentId, orderId, "New delivery assigned",
                                "You've been assigned order " + order.getOrderNumber() + " - deliver to "
                                        + order.getDeliveryAddress() + ".");
                        notificationDAO.notify("USER", order.getUserId(), orderId, "Agent assigned",
                                "A delivery agent has been assigned to your order " + order.getOrderNumber()
                                        + ". You can track it live now.");
                    }
                } catch (SQLException ignored) {
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", assigned)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId/agentId");
        }
    }

    private void writeUpdateOrderStatus(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            String status = request.getParameter("status");
            boolean updated = orderDAO.updateOrderStatus(orderId, status);

            if (updated) {
                try {
                    FuelOrder order = orderDAO.getOrderById(orderId);
                    if (order != null) {
                        notificationDAO.notify("USER", order.getUserId(), orderId, "Order update",
                                "Your order " + order.getOrderNumber() + " is now " + status.replace('_', ' ') + ".");
                    }
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

    private void writeFuelSalesReport(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> report = orderDAO.getFuelSalesReport();
        List<String> jsonObjects = new ArrayList<>();
        for (Map<String, Object> row : report) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("fuelName", (String) row.get("fuelName")),
                    JsonUtil.kvDecimal("totalLitres", (BigDecimal) row.get("totalLitres")),
                    JsonUtil.kvDecimal("totalRevenue", (BigDecimal) row.get("totalRevenue")),
                    JsonUtil.kv("totalOrders", (Number) row.get("totalOrders"))
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeMonthlySales(HttpServletResponse response) throws SQLException, IOException {
        Map<String, BigDecimal> monthly = orderDAO.getMonthlySales();
        List<String> jsonObjects = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : monthly.entrySet()) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("month", entry.getKey()),
                    JsonUtil.kvDecimal("totalSales", entry.getValue())
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeContactMessages(HttpServletResponse response) throws SQLException, IOException {
        List<ContactMessage> messages = contactMessageDAO.getAllMessages();
        List<String> jsonObjects = new ArrayList<>();
        for (ContactMessage m : messages) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("messageId", m.getMessageId()),
                    JsonUtil.kv("fullName", m.getFullName()),
                    JsonUtil.kv("email", m.getEmail()),
                    JsonUtil.kv("message", m.getMessage()),
                    JsonUtil.kv("status", m.getStatus()),
                    JsonUtil.kv("submittedAt", m.getSubmittedAt())
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeUpdateFuelPrice(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        try {
            int fuelId = Integer.parseInt(request.getParameter("fuelId"));
            BigDecimal newPrice = new BigDecimal(request.getParameter("price"));
            boolean updated = fuelTypeDAO.updatePrice(fuelId, newPrice);
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid fuelId/price");
        }
    }

    private void writeAllFeedback(HttpServletResponse response) throws SQLException, IOException {
        List<String> jsonObjects = new ArrayList<>();
        feedbackDAO.getAllFeedback().forEach(f -> jsonObjects.add(JsonUtil.object(
                JsonUtil.kv("feedbackId", f.getFeedbackId()),
                JsonUtil.kv("orderId", f.getOrderId()),
                JsonUtil.kv("userId", f.getUserId()),
                JsonUtil.kv("rating", f.getRating()),
                JsonUtil.kv("comments", f.getComments()),
                JsonUtil.kv("feedbackDate", f.getFeedbackDate())
        )));
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }
}
