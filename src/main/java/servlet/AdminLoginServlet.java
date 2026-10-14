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
 * Handles admin (role = ADMIN) login. Admins live in the same "users" table
 * as customers, distinguished only by the role column - there is no
 * separate admins table.
 */
@WebServlet("/adminLogin")
public class AdminLoginServlet extends HttpServlet {

    private final UserDAO userdao = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            response.sendRedirect("Auth_Pages.html?mode=admin&error=missing");
            return;
        }

        try {
            User user = userdao.login(email);
            // NOTE: comparing plain text here for simplicity - use BCrypt.checkpw in production.
            if (user == null || !user.isAdmin() || !user.getPassword().equals(password)) {
                response.sendRedirect("Auth_Pages.html?mode=admin&error=invalid");
                return;
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("adminId", user.getUserId());
            session.setAttribute("adminName", user.getFullName());
            session.setAttribute("adminEmail", user.getEmail());
            response.sendRedirect("Admin_Dashboard.html");
        } catch (SQLException e) {
            throw new ServletException("Database error during admin login", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("Auth_Pages.html?mode=admin");
    }
}
