package servlet;

import dao.ContactMessageDAO;
import model.ContactMessage;
import util.JsonUtil;
import util.SimpleMailSender;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/**
 * Backs the public "Get in Touch" form on Home_Page.html.
 * ContactMessageDAO already existed but nothing in the original project
 * ever called it - this is the missing wiring.
 */
@WebServlet("/contact")
public class ContactServlet extends HttpServlet {

    private final ContactMessageDAO contactMessageDAO = new ContactMessageDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");

        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String message = request.getParameter("message");

        if (fullName == null || email == null || message == null
                || fullName.isBlank() || email.isBlank() || message.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "All fields are required");
            return;
        }

        try {
            ContactMessage msg = new ContactMessage(fullName, email, message);
            int messageId = contactMessageDAO.submitMessage(msg);

            // Email the site owner so a new message doesn't just sit unnoticed in the database.
            // Configured in WEB-INF/mail.properties; if that isn't filled in yet, or the send
            // fails for any reason, the message is still safely saved above either way.
            SimpleMailSender.sendContactNotification(getServletContext(), fullName, email, message);

            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("success", messageId != -1),
                        JsonUtil.kv("messageId", messageId)
                ));
            }
        } catch (SQLException e) {
            throw new ServletException("Database error while submitting contact message", e);
        }
    }
}
