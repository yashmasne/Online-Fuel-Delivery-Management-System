package servlet;

import dao.DeliveryAgentDAO;
import dao.FuelOrderDAO;
import dao.FuelTypeDAO;
import dao.NotificationDAO;
import dao.PaymentDAO;
import model.DeliveryAgent;
import model.FuelOrder;
import model.FuelType;
import model.Payment;
import util.JsonUtil;
import util.LiveFuelPriceFetcher;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs Order_Track.html.
 *
 * This class was referenced everywhere (AdminManagementServlet, HistoryServlet,
 * TrackOrderServlet, DeliveryAgentServlet all call FuelOrderServlet.orderToJson)
 * but the file itself was missing from the project - it is written fresh here.
 *
 *   GET  ?action=fuelTypes            -> public list of fuel types + current price/litre
 *   GET  ?action=myOrder&orderId=..   -> a single order belonging to the logged-in user
 *   POST ?action=placeOrder           -> creates an order + its payment record
 *   POST ?action=cancelOrder          -> cancels a still-pending order the user owns
 */
@WebServlet("/fuelOrder")
public class FuelOrderServlet extends HttpServlet {

    private final FuelOrderDAO orderDAO = new FuelOrderDAO();
    private final FuelTypeDAO fuelTypeDAO = new FuelTypeDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final DeliveryAgentDAO agentDAO = new DeliveryAgentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        response.setContentType("application/json;charset=UTF-8");

        try {
            switch (action == null ? "" : action) {
                case "fuelTypes":
                    writeFuelTypes(response);
                    break;
                case "myOrder":
                    writeMyOrder(request, response);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (SQLException e) {
            throw new ServletException("Database error in FuelOrderServlet", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        response.setContentType("application/json;charset=UTF-8");

        try {
            switch (action == null ? "" : action) {
                case "placeOrder":
                    handlePlaceOrder(request, response);
                    break;
                case "cancelOrder":
                    handleCancelOrder(request, response);
                    break;
                default:
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (SQLException e) {
            throw new ServletException("Database error in FuelOrderServlet", e);
        }
    }

    private void writeFuelTypes(HttpServletResponse response) throws SQLException, IOException {
        List<FuelType> fuelTypes = fuelTypeDAO.getAllFuelTypes();

        // Petrol/Diesel/CNG get topped up from a live public price source when the cached
        // value is missing or more than a few hours old; LPG stays whatever the admin set.
        // A failed fetch here is swallowed inside refreshIfStale() and never breaks this page.
        LiveFuelPriceFetcher.refreshIfStale(fuelTypeDAO, fuelTypes);

        List<String> jsonObjects = new ArrayList<>();
        for (FuelType f : fuelTypes) {
            jsonObjects.add(JsonUtil.object(
                    JsonUtil.kv("fuelId", f.getFuelId()),
                    JsonUtil.kv("fuelName", f.getFuelName()),
                    JsonUtil.kvDecimal("pricePerLitre", f.getPricePerLitre()),
                    JsonUtil.kv("description", f.getDescription()),
                    JsonUtil.kv("priceUpdatedAt", f.getPriceUpdatedAt()),
                    JsonUtil.kv("isLive", f.getPriceUpdatedAt() != null)
            ));
        }
        try (PrintWriter out = response.getWriter()) {
            out.print(JsonUtil.array(jsonObjects));
        }
    }

    private void writeMyOrder(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        Integer userId = requireLoggedInUser(request, response);
        if (userId == null) {
            return;
        }
        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            FuelOrder order = orderDAO.getOrderById(orderId);
            if (order == null || order.getUserId() != userId) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Order not found");
                return;
            }
            try (PrintWriter out = response.getWriter()) {
                out.print(orderToJson(order));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        }
    }

    private void handlePlaceOrder(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer userId = requireLoggedInUser(request, response);
        if (userId == null) {
            return;
        }

        try {
            int fuelId = Integer.parseInt(request.getParameter("fuelId"));
            BigDecimal quantity = new BigDecimal(request.getParameter("quantity"));
            String vehicleNumber = request.getParameter("vehicleNumber");
            String vehicleType = request.getParameter("vehicleType");
            String deliveryAddress = request.getParameter("deliveryAddress");
            String paymentMethod = request.getParameter("paymentMethod"); // CASH, CARD, UPI, WALLET

            String latParam = request.getParameter("latitude");
            String lngParam = request.getParameter("longitude");
            BigDecimal latitude = (latParam == null || latParam.isBlank()) ? BigDecimal.ZERO : new BigDecimal(latParam);
            BigDecimal longitude = (lngParam == null || lngParam.isBlank()) ? BigDecimal.ZERO : new BigDecimal(lngParam);

            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Quantity must be greater than zero");
                return;
            }
            if (deliveryAddress == null || deliveryAddress.isBlank()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Delivery address is required");
                return;
            }
            if (paymentMethod == null || paymentMethod.isBlank()) {
                paymentMethod = "UPI";
            }

            FuelType fuelType = fuelTypeDAO.getFuelTypeById(fuelId);
            if (fuelType == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown fuel type");
                return;
            }

            BigDecimal totalAmount = fuelType.getPricePerLitre()
                    .multiply(quantity)
                    .setScale(2, RoundingMode.HALF_UP);

            FuelOrder order = new FuelOrder();
            order.setUserId(userId);
            order.setFuelId(fuelId);
            order.setVehicleNumber(vehicleNumber);
            order.setVehicleType(vehicleType);
            order.setQuantity(quantity);
            order.setPricePerLitre(fuelType.getPricePerLitre());
            order.setTotalAmount(totalAmount);
            order.setDeliveryAddress(deliveryAddress);
            order.setLatitude(latitude);
            order.setLongitude(longitude);
            order.setPaymentMethod(paymentMethod);

            int orderId = orderDAO.placeOrder(order);
            if (orderId == -1) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not place order");
                return;
            }

            // COD-style methods settle at delivery time; UPI/CARD/WALLET are treated
            // as paid up-front for this simulated checkout flow.
            String paymentStatus = "CASH".equalsIgnoreCase(paymentMethod) ? "PENDING" : "PAID";
            Payment payment = new Payment(orderId, null, paymentMethod, totalAmount, paymentStatus);
            paymentDAO.recordPayment(payment);
            orderDAO.updatePaymentStatus(orderId, paymentStatus);

            FuelOrder created = orderDAO.getOrderById(orderId);

            // Notify the customer their order was placed, and let admins know a new
            // order is waiting to be assigned. A notification failure here must never
            // block the order that was already saved, so it is swallowed on its own.
            try {
                notificationDAO.notify("USER", userId, orderId, "Order placed",
                        "Your order " + created.getOrderNumber() + " for " + quantity
                                + " L " + fuelType.getFuelName() + " has been placed. "
                                + "We'll notify you as soon as a delivery agent is assigned.");
                notificationDAO.notify("ADMIN", 0, orderId, "New fuel order",
                        "Order " + created.getOrderNumber() + " needs a delivery agent assigned.");
            } catch (SQLException ignored) {
            }

            // If the customer shared their live location, try to auto-dispatch the nearest
            // available agent right away instead of waiting for an admin to pick one by hand.
            // If no agent has shared a location yet (or none are AVAILABLE), this quietly does
            // nothing and the order is left exactly as before - pending, for an admin to assign.
            boolean autoAssigned = false;
            if (latitude.compareTo(BigDecimal.ZERO) != 0 || longitude.compareTo(BigDecimal.ZERO) != 0) {
                try {
                    autoAssigned = autoAssignNearestAgent(created, latitude, longitude);
                    created = orderDAO.getOrderById(orderId); // refresh so the response reflects the assignment
                } catch (SQLException ignored) {
                }
            }

            // No specific agent was close enough to auto-dispatch (or no agent has shared a
            // location yet) - let every available agent know an order is up for grabs, so one
            // of them can accept it themselves from their dashboard instead of waiting on admin.
            if (!autoAssigned) {
                try {
                    for (var agent : agentDAO.getAvailableAgents()) {
                        notificationDAO.notify("AGENT", agent.getAgentId(), orderId, "New order available",
                                "Order " + created.getOrderNumber() + " to " + created.getDeliveryAddress()
                                        + " is unassigned - open your dashboard to accept it.");
                    }
                } catch (SQLException ignored) {
                }
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("success", true),
                        "\"order\":" + orderToJson(created)
                ));
            }

        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid numeric input");
        }
    }

    /**
     * Finds the AVAILABLE delivery agent physically closest to the customer's shared location
     * and assigns them to this order, mirroring exactly what an admin manually assigning an
     * agent does (same DB update, same two notifications) - just triggered automatically at
     * order-placement time instead of waiting for a person to do it. An admin can still
     * reassign the order afterwards from Admin_Management.html if needed.
     */
    private boolean autoAssignNearestAgent(FuelOrder order, BigDecimal customerLat, BigDecimal customerLng)
            throws SQLException {
        DeliveryAgent nearest = agentDAO.findNearestAvailable(customerLat, customerLng);
        if (nearest == null) {
            return false; // no agent has shared a location yet, or none are free - leave for admin/agents to pick up
        }

        boolean assigned = orderDAO.assignAgentToOrder(order.getOrderId(), nearest.getAgentId());
        if (!assigned) {
            return false;
        }

        try {
            notificationDAO.notify("AGENT", nearest.getAgentId(), order.getOrderId(), "New delivery assigned",
                    "You've been auto-assigned order " + order.getOrderNumber() + " (nearest available agent) - deliver to "
                            + order.getDeliveryAddress() + ".");
            notificationDAO.notify("USER", order.getUserId(), order.getOrderId(), "Agent assigned",
                    "The nearest available delivery agent, " + nearest.getFullName()
                            + ", has been assigned to your order " + order.getOrderNumber() + ". You can track it live now.");
        } catch (SQLException ignored) {
        }
        return true;
    }

    private void handleCancelOrder(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {

        Integer userId = requireLoggedInUser(request, response);
        if (userId == null) {
            return;
        }

        try {
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            FuelOrder order = orderDAO.getOrderById(orderId);
            if (order == null || order.getUserId() != userId) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not your order");
                return;
            }
            if (!"PENDING".equals(order.getOrderStatus()) && !"CONFIRMED".equals(order.getOrderStatus())) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Order can no longer be cancelled");
                return;
            }
            boolean cancelled = orderDAO.updateOrderStatus(orderId, "CANCELLED");
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", cancelled)));
            }
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        }
    }

    private Integer requireLoggedInUser(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return null;
        }
        return (Integer) session.getAttribute("userId");
    }

    /** Shared JSON shape for a FuelOrder, used by every servlet that returns orders. */
    public static String orderToJson(FuelOrder o) {
        return JsonUtil.object(
                JsonUtil.kv("orderId", o.getOrderId()),
                JsonUtil.kv("orderNumber", o.getOrderNumber()),
                JsonUtil.kv("userId", o.getUserId()),
                JsonUtil.kv("agentId", o.getAgentId()),
                JsonUtil.kv("customerName", o.getCustomerName()),
                JsonUtil.kv("agentName", o.getAgentName()),
                JsonUtil.kv("fuelId", o.getFuelId()),
                JsonUtil.kv("fuelName", o.getFuelName()),
                JsonUtil.kv("vehicleNumber", o.getVehicleNumber()),
                JsonUtil.kv("vehicleType", o.getVehicleType()),
                JsonUtil.kvDecimal("quantity", o.getQuantity()),
                JsonUtil.kvDecimal("pricePerLitre", o.getPricePerLitre()),
                JsonUtil.kvDecimal("totalAmount", o.getTotalAmount()),
                JsonUtil.kv("deliveryAddress", o.getDeliveryAddress()),
                JsonUtil.kvDecimal("latitude", o.getLatitude()),
                JsonUtil.kvDecimal("longitude", o.getLongitude()),
                JsonUtil.kv("paymentMethod", o.getPaymentMethod()),
                JsonUtil.kv("paymentStatus", o.getPaymentStatus()),
                JsonUtil.kv("orderStatus", o.getOrderStatus()),
                JsonUtil.kv("orderDate", o.getOrderDate()),
                JsonUtil.kv("deliveryDate", o.getDeliveryDate())
        );
    }
}
