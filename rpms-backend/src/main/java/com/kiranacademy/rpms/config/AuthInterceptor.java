package com.kiranacademy.rpms.config;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.kiranacademy.rpms.service.TokenSessionManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenSessionManager tokenSessionManager;

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health",
            "/api/test",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/skills",
            "/api/qualifications",
            "/api/roles"
    );

    public AuthInterceptor(TokenSessionManager tokenSessionManager) {
        this.tokenSessionManager = tokenSessionManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            return true;
        }

        Map<String, Object> currentUser = resolveUser(request);
        if (currentUser == null) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required. Please sign in.");
            return false;
        }

        request.setAttribute("currentUser", currentUser);

        // Role-based authorization
        String roleCode = (String) currentUser.get("roleCode");
        if (roleCode == null) {
            roleCode = (String) currentUser.get("role_code");
        }

        // Candidates cannot access administrative reports or modify corporate partners/jobs
        if ("CANDIDATE".equalsIgnoreCase(roleCode)) {
            if (path.startsWith("/api/reports") ||
                (path.startsWith("/api/companies") && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) ||
                (path.startsWith("/api/jobs") && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)))) {
                sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied. Candidate role lacks administrative privileges.");
                return false;
            }
        }

        return true;
    }

    private boolean isPublicPath(String path) {
        for (String pub : PUBLIC_PATHS) {
            if (path.equalsIgnoreCase(pub) || path.startsWith(pub + "/")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveUser(HttpServletRequest request) {
        // 1. Check HTTP Session
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionUser = session.getAttribute("currentUser");
            if (sessionUser instanceof Map) {
                return (Map<String, Object>) sessionUser;
            }
        }

        // 2. Check X-Auth-Token header
        String token = request.getHeader("X-Auth-Token");
        if (token != null && !token.isBlank()) {
            Map<String, Object> user = tokenSessionManager.validateToken(token.trim());
            if (user != null) return user;
        }

        // 3. Check Authorization Bearer header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String bearerToken = authHeader.substring(7).trim();
            return tokenSessionManager.validateToken(bearerToken);
        }

        return null;
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String errorType = (status == 401) ? "Unauthorized" : "Forbidden";
        String json = "{\"status\":" + status + ",\"error\":\"" + errorType + "\",\"message\":\"" + escapeJson(message) + "\"}";
        response.getWriter().write(json);
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
