package servlet;

import dao.UserDAO;
import model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Handles new customer registration from Auth_Pages.html.
 * Role is always forced to USER - admin/agent accounts are not self-service.
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private final UserDAO userdao = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");
        String address = request.getParameter("address");
        String city = request.getParameter("city");
        String pincode = request.getParameter("pincode");

        if (fullName == null || email == null || phone == null || password == null
                || fullName.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            response.sendRedirect("Auth_Pages.html?mode=register&error=missing");
            return;
        }

        try {
            if (userdao.emailExists(email)) {
                response.sendRedirect("Auth_Pages.html?mode=register&error=exists");
                return;
            }
            // NOTE: store a hashed password in production, e.g. BCrypt.hashpw(password, BCrypt.gensalt())
            User newUser = new User(fullName, email, phone, password, address, city, pincode);
            int userId = userdao.registerUser(newUser);
            if (userId == -1) {
                response.sendRedirect("Auth_Pages.html?mode=register&error=failed");
                return;
            }
            response.sendRedirect("Auth_Pages.html?mode=login&registered=true");
        } catch (SQLException e) {
            throw new ServletException("Database error during registration", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("Auth_Pages.html?mode=register");
    }
}
