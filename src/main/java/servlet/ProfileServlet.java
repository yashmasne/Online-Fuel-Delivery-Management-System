package servlet;

import dao.UserDAO;
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
import java.sql.SQLException;

/**
 * Backs the profile section of History_Profile.html - lets the logged-in
 * customer view and edit their own account details.
 */
@WebServlet("/profile")
public class ProfileServlet extends HttpServlet {

    private final UserDAO userdao = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer userId = requireLoggedInUser(request, response);
        if (userId == null) {
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        try {
            User user = userdao.getUserById(userId);
            if (user == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
                return;
            }
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(
                        JsonUtil.kv("userId", user.getUserId()),
                        JsonUtil.kv("fullName", user.getFullName()),
                        JsonUtil.kv("email", user.getEmail()),
                        JsonUtil.kv("phone", user.getPhone()),
                        JsonUtil.kv("address", user.getAddress()),
                        JsonUtil.kv("city", user.getCity()),
                        JsonUtil.kv("pincode", user.getPincode()),
                        JsonUtil.kv("status", user.getStatus()),
                        JsonUtil.kv("createdAt", user.getCreatedAt())
                ));
            }
        } catch (SQLException e) {
            throw new ServletException("Database error while fetching profile", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer userId = requireLoggedInUser(request, response);
        if (userId == null) {
            return;
        }
        response.setContentType("application/json;charset=UTF-8");
        try {
            User user = new User();
            user.setUserId(userId);
            user.setFullName(request.getParameter("fullName"));
            user.setPhone(request.getParameter("phone"));
            user.setAddress(request.getParameter("address"));
            user.setCity(request.getParameter("city"));
            user.setPincode(request.getParameter("pincode"));
            boolean updated = userdao.updateProfile(user);
            HttpSession session = request.getSession(false);
            if (updated && session != null) {
                session.setAttribute("userName", user.getFullName());
            }
            try (PrintWriter out = response.getWriter()) {
                out.print(JsonUtil.object(JsonUtil.kv("success", updated)));
            }
        } catch (SQLException e) {
            throw new ServletException("Database error while updating profile", e);
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
}
