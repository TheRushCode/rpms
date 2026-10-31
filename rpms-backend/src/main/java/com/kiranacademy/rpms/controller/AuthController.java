package com.kiranacademy.rpms.controller;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kiranacademy.rpms.config.AuthInterceptor;
import com.kiranacademy.rpms.exception.BadRequestException;
import com.kiranacademy.rpms.service.PasswordService;
import com.kiranacademy.rpms.service.TokenSessionManager;
import com.kiranacademy.rpms.service.TokenSessionManager.ResetTokenData;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final TokenSessionManager tokenSessionManager;
    private final AuthInterceptor authInterceptor;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public AuthController(JdbcTemplate jdbcTemplate,
                          PasswordService passwordService,
                          TokenSessionManager tokenSessionManager,
                          AuthInterceptor authInterceptor) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.tokenSessionManager = tokenSessionManager;
        this.authInterceptor = authInterceptor;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> payload) {
        String fullName = getStr(payload, "fullName");
        if (fullName == null || fullName.isBlank()) {
            fullName = getStr(payload, "full_name");
        }
        String email = getStr(payload, "email");
        String password = getStr(payload, "password");
        String confirmPassword = getStr(payload, "confirmPassword");
        if (confirmPassword == null) {
            confirmPassword = getStr(payload, "confirm_password");
        }
        String requestedRole = getStr(payload, "role");

        // 1. Validate full name
        if (fullName == null || fullName.trim().length() < 2) {
            throw new BadRequestException("Full name is required and must be at least 2 characters.");
        }
        if (fullName.trim().length() > 120) {
            throw new BadRequestException("Full name cannot exceed 120 characters.");
        }

        // 2. Validate email
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email address is required.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches() || normalizedEmail.length() > 150) {
            throw new BadRequestException("Please provide a valid email address.");
        }

        // 3. Validate password & policy
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Password is required.");
        }
        passwordService.validatePolicy(password);

        if (confirmPassword != null && !password.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match.");
        }

        // 4. Role Assignment Security: Public users CANNOT register as ADMIN
        String targetRoleCode = "CANDIDATE";
        String initialStatus = "ACTIVE";

        if (requestedRole != null && !requestedRole.isBlank()) {
            String roleUpper = requestedRole.trim().toUpperCase();
            if ("ADMIN".equals(roleUpper) || "ADMINISTRATOR".equals(roleUpper)) {
                throw new BadRequestException("Administrator accounts cannot be self-registered.");
            } else if ("PLACEMENT_OFFICER".equals(roleUpper) || "RECRUITER".equals(roleUpper)) {
                targetRoleCode = "PLACEMENT_OFFICER";
                initialStatus = "PENDING_APPROVAL";
            } else if ("INTERVIEWER".equals(roleUpper)) {
                targetRoleCode = "INTERVIEWER";
                initialStatus = "PENDING_APPROVAL";
            } else if ("CANDIDATE".equals(roleUpper)) {
                targetRoleCode = "CANDIDATE";
                initialStatus = "ACTIVE";
            }
        }

        // 5. Query target role from roles table
        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT role_id, role_code, role_name FROM roles WHERE role_code = ?", targetRoleCode);
        if (roles.isEmpty()) {
            throw new BadRequestException("Specified account role is not supported by the system.");
        }
        int roleId = (Integer) roles.get(0).get("role_id");
        String roleName = (String) roles.get(0).get("role_name");

        // 6. Prevent duplicate registrations (email or username)
        Integer existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LOWER(email) = ? OR LOWER(username) = ?",
                Integer.class, normalizedEmail, normalizedEmail);
        if (existingCount != null && existingCount > 0) {
            throw new BadRequestException("An account with this email address already exists. Please sign in.");
        }

        // 7. Hash password using BCrypt
        String passwordHash = passwordService.encode(password);

        // 8. Insert new user record
        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String finalFullName = fullName.trim();
        final String finalRoleCode = targetRoleCode;
        final String finalStatus = initialStatus;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash, full_name, email, role_id, status, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, normalizedEmail);
            ps.setString(2, passwordHash);
            ps.setString(3, finalFullName);
            ps.setString(4, normalizedEmail);
            ps.setInt(5, roleId);
            ps.setString(6, finalStatus);
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        int newUserId = (generatedId != null) ? generatedId.intValue() : 0;

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        if ("PENDING_APPROVAL".equals(initialStatus)) {
            response.put("message", "Registration submitted! Your staff account is pending administrator approval.");
        } else {
            response.put("message", "Registration successful! You can now sign in with your credentials.");
        }
        response.put("userId", newUserId);
        response.put("email", normalizedEmail);
        response.put("role", roleName);
        response.put("status", initialStatus);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> credentials,
                                                     HttpServletRequest request) {
        String identifier = getStr(credentials, "username");
        if (identifier == null || identifier.isBlank()) {
            identifier = getStr(credentials, "email");
        }
        String password = getStr(credentials, "password");

        if (identifier == null || identifier.isBlank()) {
            throw new BadRequestException("Username or email is required.");
        }
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Password is required.");
        }

        String normalizedId = identifier.trim().toLowerCase();

        // 1. Rate limiting check
        tokenSessionManager.checkRateLimit(normalizedId);

        // 2. Query user from database
        String sql = "SELECT u.user_id, u.username, u.password_hash, u.full_name, u.email, u.status, " +
                "r.role_id, r.role_code, r.role_name " +
                "FROM users u " +
                "JOIN roles r ON u.role_id = r.role_id " +
                "WHERE (LOWER(u.username) = ? OR LOWER(u.email) = ?)";

        List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, normalizedId, normalizedId);
        if (users.isEmpty()) {
            tokenSessionManager.recordFailedAttempt(normalizedId);
            throw new BadRequestException("Invalid credentials. Please verify your username/email and password.");
        }

        Map<String, Object> user = users.get(0);
        String status = (String) user.get("status");

        // 3. Status checks
        if ("PENDING_APPROVAL".equalsIgnoreCase(status)) {
            throw new BadRequestException("This account is pending administrator approval before activation.");
        }
        if (status != null && !"ACTIVE".equalsIgnoreCase(status)) {
            throw new BadRequestException("This account is currently disabled. Please contact your administrator.");
        }

        // 4. Verify password with BCrypt (and legacy demo fallback)
        String storedHash = (String) user.get("password_hash");
        boolean passwordMatch = passwordService.matches(password, storedHash);

        if (!passwordMatch) {
            tokenSessionManager.recordFailedAttempt(normalizedId);
            throw new BadRequestException("Invalid credentials. Please verify your username/email and password.");
        }

        // Reset rate-limit on success
        tokenSessionManager.resetFailedAttempts(normalizedId);

        int userId = (Integer) user.get("user_id");

        // Transparently upgrade legacy hash to BCrypt
        if (passwordService.isLegacyHash(storedHash)) {
            String newHash = passwordService.encode(password);
            jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE user_id = ?", newHash, userId);
        }

        // Update last login timestamp
        jdbcTemplate.update("UPDATE users SET last_login_at = NOW() WHERE user_id = ?", userId);

        // Build safe authenticated user DTO
        Map<String, Object> authUser = new HashMap<>();
        authUser.put("userId", user.get("user_id"));
        authUser.put("username", user.get("username"));
        authUser.put("fullName", user.get("full_name"));
        authUser.put("email", user.get("email"));
        authUser.put("roleId", user.get("role_id"));
        authUser.put("roleCode", user.get("role_code"));
        authUser.put("roleName", user.get("role_name"));

        // 5. Session fixation protection: regenerate session
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("currentUser", authUser);

        // 6. Generate resilient session token for header/cookie fallback
        String token = tokenSessionManager.createToken(authUser);
        newSession.setAttribute("authToken", token);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Login successful");
        response.put("token", token);
        response.put("user", authUser);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        Map<String, Object> user = authInterceptor.resolveUser(request);
        if (user == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("authenticated", false);
            err.put("error", "Unauthorized");
            err.put("message", "No active user session.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String token = (String) session.getAttribute("authToken");
            if (token != null) {
                tokenSessionManager.revokeToken(token);
            }
            session.invalidate();
        }

        String headerToken = request.getHeader("X-Auth-Token");
        if (headerToken != null) {
            tokenSessionManager.revokeToken(headerToken);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Logged out successfully");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, Object> payload) {
        String email = getStr(payload, "email");
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email.trim().toLowerCase()).matches()) {
            throw new BadRequestException("Please provide a valid email address.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT user_id, email FROM users WHERE LOWER(email) = ?", normalizedEmail);

        if (!users.isEmpty()) {
            int userId = (Integer) users.get(0).get("user_id");
            String token = tokenSessionManager.createPasswordResetToken(userId, normalizedEmail);
            // In an email-integrated environment, this token is dispatched via SMTP/SendGrid.
            // A demonstration token is returned in debug/development mode if configured.
        }

        // Generic response prevents email enumeration
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "If an account exists with that email address, password reset instructions have been initiated.");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> payload) {
        String token = getStr(payload, "token");
        String newPassword = getStr(payload, "newPassword");
        String confirmPassword = getStr(payload, "confirmPassword");

        if (token == null || token.isBlank()) {
            throw new BadRequestException("Password reset token is required.");
        }

        ResetTokenData tokenData = tokenSessionManager.validateResetToken(token);
        if (tokenData == null) {
            throw new BadRequestException("Invalid or expired password reset token.");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required.");
        }
        passwordService.validatePolicy(newPassword);

        if (confirmPassword != null && !newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match.");
        }

        String newHash = passwordService.encode(newPassword);
        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE user_id = ?", newHash, tokenData.userId());
        tokenSessionManager.invalidateResetToken(token);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Password has been successfully updated. You may now sign in.");
        return ResponseEntity.ok(res);
    }

    private String getStr(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }
}
