package com.kiranacademy.rpms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.kiranacademy.rpms.config.AuthInterceptor;
import com.kiranacademy.rpms.controller.AuthController;
import com.kiranacademy.rpms.exception.BadRequestException;
import com.kiranacademy.rpms.service.PasswordService;
import com.kiranacademy.rpms.service.TokenSessionManager;

class AuthIntegrationTests {

    private PasswordService passwordService;
    private TokenSessionManager tokenSessionManager;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
        tokenSessionManager = new TokenSessionManager();
    }

    @Test
    void testPasswordHashing_BCrypt() {
        String raw = "StrongPass123";
        String encoded = passwordService.encode(raw);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$"), "Hash should start with $2a$");
        assertTrue(passwordService.matches(raw, encoded));
        assertFalse(passwordService.matches("WrongPass123", encoded));
    }

    @Test
    void testPasswordPolicy_Validation() {
        // Too short
        assertThrows(BadRequestException.class, () -> passwordService.validatePolicy("Short1"));
        // No digit
        assertThrows(BadRequestException.class, () -> passwordService.validatePolicy("NoDigitsHere"));
        // No letter
        assertThrows(BadRequestException.class, () -> passwordService.validatePolicy("1234567890"));
        // Valid
        passwordService.validatePolicy("ValidPass123");
    }

    @Test
    void testLegacyDemoHash_Compatibility() {
        assertTrue(passwordService.matches("password", "DEMO_HASH_REPLACE_BEFORE_LOGIN"));
        assertTrue(passwordService.matches("admin123", "DEMO_HASH_REPLACE_BEFORE_LOGIN"));
        assertFalse(passwordService.matches("randomWrongPassword", "DEMO_HASH_REPLACE_BEFORE_LOGIN"));
        assertTrue(passwordService.isLegacyHash("DEMO_HASH_REPLACE_BEFORE_LOGIN"));
    }

    @Test
    void testTokenSessionManager_CreateAndValidate() {
        Map<String, Object> user = Map.of("userId", 42, "email", "test@kiranacademy.com", "roleCode", "CANDIDATE");
        String token = tokenSessionManager.createToken(user);

        assertNotNull(token);
        Map<String, Object> validated = tokenSessionManager.validateToken(token);
        assertNotNull(validated);
        assertEquals(42, validated.get("userId"));

        // Revoke
        tokenSessionManager.revokeToken(token);
        assertNull(tokenSessionManager.validateToken(token));
    }

    @Test
    void testTokenSessionManager_RateLimiting() {
        String key = "test_user@example.com";
        // 4 failed attempts should not lock
        for (int i = 0; i < 4; i++) {
            tokenSessionManager.recordFailedAttempt(key);
        }
        tokenSessionManager.checkRateLimit(key); // should pass without exception

        // 5th failed attempt locks out
        tokenSessionManager.recordFailedAttempt(key);
        assertThrows(BadRequestException.class, () -> tokenSessionManager.checkRateLimit(key));

        // Reset works
        tokenSessionManager.resetFailedAttempts(key);
        tokenSessionManager.checkRateLimit(key); // passes
    }

    @Test
    void testAuthInterceptor_PublicPathsAllowed() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(tokenSessionManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void testAuthInterceptor_ProtectedPathRejectsUnauthenticated() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(tokenSessionManager);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/candidates");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
    }

    @Test
    void testAuthInterceptor_ProtectedPathAcceptsValidToken() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(tokenSessionManager);
        Map<String, Object> user = Map.of("userId", 1, "roleCode", "ADMIN");
        String token = tokenSessionManager.createToken(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/candidates");
        request.addHeader("X-Auth-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void testAuthInterceptor_RoleBasedAccess_CandidateForbiddenFromAdminReports() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(tokenSessionManager);
        Map<String, Object> candidateUser = Map.of("userId", 10, "roleCode", "CANDIDATE");
        String token = tokenSessionManager.createToken(candidateUser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reports/funnel");
        request.addHeader("X-Auth-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
    }

    @Test
    void testPasswordResetFlow() {
        String token = tokenSessionManager.createPasswordResetToken(99, "reset@example.com");
        assertNotNull(token);

        var data = tokenSessionManager.validateResetToken(token);
        assertNotNull(data);
        assertEquals(99, data.userId());
        assertEquals("reset@example.com", data.email());

        tokenSessionManager.invalidateResetToken(token);
        assertNull(tokenSessionManager.validateResetToken(token));
    }

    @Test
    void testRegistrationValidation_AdminRoleForbidden() {
        AuthController authController = new AuthController(null, passwordService, tokenSessionManager, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("fullName", "Attacker");
        payload.put("email", "attacker@example.com");
        payload.put("password", "SecretPass123");
        payload.put("confirmPassword", "SecretPass123");
        payload.put("role", "ADMIN");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authController.register(payload));
        assertTrue(ex.getMessage().contains("Administrator accounts cannot be self-registered"));
    }

    @Test
    void testRegistrationValidation_InvalidEmail() {
        AuthController authController = new AuthController(null, passwordService, tokenSessionManager, null);
        Map<String, Object> payload = Map.of(
                "fullName", "John Doe",
                "email", "not-a-valid-email",
                "password", "SecretPass123",
                "confirmPassword", "SecretPass123"
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authController.register(payload));
        assertTrue(ex.getMessage().contains("valid email address"));
    }

    @Test
    void testRegistrationValidation_PasswordMismatch() {
        AuthController authController = new AuthController(null, passwordService, tokenSessionManager, null);
        Map<String, Object> payload = Map.of(
                "fullName", "John Doe",
                "email", "john@example.com",
                "password", "SecretPass123",
                "confirmPassword", "MismatchPass456"
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authController.register(payload));
        assertTrue(ex.getMessage().contains("Passwords do not match"));
    }
}
