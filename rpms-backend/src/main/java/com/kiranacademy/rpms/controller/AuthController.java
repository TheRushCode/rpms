package com.kiranacademy.rpms.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kiranacademy.rpms.exception.BadRequestException;
import com.kiranacademy.rpms.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;

    public AuthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> credentials, HttpSession session) {
        String username = getStr(credentials, "username");
        String password = getStr(credentials, "password");
        String roleStr = getStr(credentials, "role");

        if (username == null || username.isBlank()) {
            throw new BadRequestException("Username or email is required.");
        }
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Password is required.");
        }

        String sql = "SELECT u.user_id, u.username, u.password_hash, u.full_name, u.email, u.status, " +
                "r.role_id, r.role_code, r.role_name " +
                "FROM users u " +
                "JOIN roles r ON u.role_id = r.role_id " +
                "WHERE (LOWER(u.username) = ? OR LOWER(u.email) = ?)";

        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, username.toLowerCase().trim(), username.toLowerCase().trim());
        if (users.isEmpty()) {
            throw new BadRequestException("Invalid credentials. User not found.");
        }

        Map<String, Object> user = users.get(0);
        String status = (String) user.get("status");
        if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new BadRequestException("This account is currently inactive. Contact your administrator.");
        }

        String storedHash = (String) user.get("password_hash");
        boolean passwordMatch = verifyPassword(password, storedHash);

        if (!passwordMatch) {
            throw new BadRequestException("Invalid credentials. Please verify your password.");
        }

        // Update last login timestamp
        int userId = (Integer) user.get("user_id");
        jdbcTemplate.update("UPDATE users SET last_login_at = NOW() WHERE user_id = ?", userId);

        // Build safe response (no password_hash)
        Map<String, Object> authUser = new HashMap<>();
        authUser.put("userId", user.get("user_id"));
        authUser.put("username", user.get("username"));
        authUser.put("fullName", user.get("full_name"));
        authUser.put("email", user.get("email"));
        authUser.put("roleId", user.get("role_id"));
        authUser.put("roleCode", user.get("role_code"));
        authUser.put("roleName", user.get("role_name"));

        session.setAttribute("currentUser", authUser);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Login successful");
        response.put("user", authUser);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        Object user = session.getAttribute("currentUser");
        if (user == null) {
            // Default demo identity if session not established
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT u.user_id, u.username, u.full_name, u.email, r.role_code, r.role_name " +
                    "FROM users u JOIN roles r ON u.role_id = r.role_id WHERE u.user_id = 1");
            if (!users.isEmpty()) {
                return ResponseEntity.ok(users.get(0));
            }
            throw new ResourceNotFoundException("No active user session.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) user;
        return ResponseEntity.ok(userMap);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Logged out successfully");
        return ResponseEntity.ok(res);
    }

    private boolean verifyPassword(String inputPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }

        // Support existing template dummy placeholder
        if ("DEMO_HASH_REPLACE_BEFORE_LOGIN".equalsIgnoreCase(storedHash)) {
            // For educational prototype demo accounts, only accept standard demo password
            return "password".equals(inputPassword) || 
                   "admin123".equals(inputPassword) || 
                   "demo123".equals(inputPassword);
        }

        // SHA-256 check
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(inputPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            if (hexString.toString().equalsIgnoreCase(storedHash)) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return inputPassword.equals(storedHash);
    }

    private String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }
}
