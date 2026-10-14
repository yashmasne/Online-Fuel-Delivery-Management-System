package servlet;

import dao.FuelOrderDAO;
import dao.PaymentDAO;
import model.Payment;
import util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * Handles payment status lookups/updates (e.g. a simulated payment gateway
 * callback marking a payment PAID or FAILED).
 * Keeps fuel_orders.payment_status in sync with payments.payment_status.
 */
@WebServlet("/payment")
public class PaymentServlet extends HttpServlet {

    private final PaymentDAO paymentdao = new PaymentDAO();
    private final FuelOrderDAO orderdao = new FuelOrderDAO();

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
            Payment payment = paymentdao.getPaymentByOrderId(orderId);

            if (payment == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No payment found for this order");
                return;
            }

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("paymentId", payment.getPaymentId()),
                        JsonUtil.kv("orderId", payment.getOrderId()),
                        JsonUtil.kv("transactionId", payment.getTransactionId()),
                        JsonUtil.kv("paymentMethod", payment.getPaymentMethod()),
                        JsonUtil.kvDecimal("amount", payment.getAmount()),
                        JsonUtil.kv("paymentStatus", payment.getPaymentStatus()),
                        JsonUtil.kv("paymentDate", payment.getPaymentDate())
                ));
            }

        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid orderId");
        } catch (SQLException e) {
            throw new ServletException("Database error while fetching payment", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        try {
            int paymentId = Integer.parseInt(request.getParameter("paymentId"));
            int orderId = Integer.parseInt(request.getParameter("orderId"));
            String status = request.getParameter("status");

            // status here is a PAYMENT status (PENDING/PAID/FAILED/REFUNDED) - keep it out
            // of fuel_orders.order_status, which uses a different workflow enum.
            boolean paymentupdated = paymentdao.updatePaymentStatus(paymentId, status);
            boolean orderupdated = orderdao.updatePaymentStatus(orderId, status);

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("success", paymentupdated && orderupdated),
                        JsonUtil.kv("paymentId", paymentId),
                        JsonUtil.kv("orderId", orderId),
                        JsonUtil.kv("status", status)
                ));
            }

        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid paymentId/orderId");
        } catch (SQLException e) {
            throw new ServletException("Database error while updating payment", e);
        }
    }
}
