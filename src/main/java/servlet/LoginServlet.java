package servlet;

import dao.UserDAO;
import model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Handles customer (role = USER) login from Auth_Pages.html.
 * Admin login is handled separately by AdminLoginServlet.
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            response.sendRedirect("Auth_Pages.html?mode=login&error=missing");
            return;
        }

        try {
            User user = userDAO.login(email);
            // NOTE: comparing plain text here for simplicity. In production,
            // store password hashes with BCrypt and use BCrypt.checkpw(password, user.getPassword()).
            if (user == null || user.isAdmin() || !user.getPassword().equals(password)) {
                response.sendRedirect("Auth_Pages.html?mode=login&error=invalid");
                return;
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("userId", user.getUserId());
            session.setAttribute("userName", user.getFullName());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userRole", user.getRole());
            response.sendRedirect("User_Dashboard.html");
        } catch (SQLException e) {
            throw new ServletException("Database error during login", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("Auth_Pages.html?mode=login");
    }
}
