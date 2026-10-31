package com.kiranacademy.rpms.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.kiranacademy.rpms.exception.BadRequestException;

@Service
public class TokenSessionManager {

    private static final long SESSION_TTL_SECONDS = 86400; // 24 hours
    private static final long RESET_TOKEN_TTL_SECONDS = 900; // 15 minutes
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 900; // 15 minutes

    public record UserSession(Map<String, Object> user, Instant expiresAt) {}
    public record ResetTokenData(int userId, String email, Instant expiresAt) {}
    public record LoginAttempt(int count, Instant lockedUntil) {}

    private final Map<String, UserSession> activeTokens = new ConcurrentHashMap<>();
    private final Map<String, ResetTokenData> resetTokens = new ConcurrentHashMap<>();
    private final Map<String, LoginAttempt> loginAttempts = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String createToken(Map<String, Object> user) {
        String token = UUID.randomUUID().toString().replace("-", "") +
                       Long.toHexString(System.currentTimeMillis());
        activeTokens.put(token, new UserSession(user, Instant.now().plusSeconds(SESSION_TTL_SECONDS)));
        return token;
    }

    public Map<String, Object> validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession session = activeTokens.get(token);
        if (session == null) return null;
        if (Instant.now().isAfter(session.expiresAt())) {
            activeTokens.remove(token);
            return null;
        }
        // Slide expiration window
        activeTokens.put(token, new UserSession(session.user(), Instant.now().plusSeconds(SESSION_TTL_SECONDS)));
        return session.user();
    }

    public void revokeToken(String token) {
        if (token != null) {
            activeTokens.remove(token);
        }
    }

    public void checkRateLimit(String key) {
        if (key == null || key.isBlank()) return;
        LoginAttempt attempt = loginAttempts.get(key.toLowerCase().trim());
        if (attempt != null && attempt.lockedUntil() != null) {
            if (Instant.now().isBefore(attempt.lockedUntil())) {
                throw new BadRequestException("Too many failed login attempts. Please wait 15 minutes before trying again.");
            } else {
                loginAttempts.remove(key.toLowerCase().trim());
            }
        }
    }

    public void recordFailedAttempt(String key) {
        if (key == null || key.isBlank()) return;
        String normalizedKey = key.toLowerCase().trim();
        loginAttempts.compute(normalizedKey, (k, existing) -> {
            int newCount = (existing == null) ? 1 : existing.count() + 1;
            if (newCount >= MAX_FAILED_ATTEMPTS) {
                return new LoginAttempt(newCount, Instant.now().plusSeconds(LOCKOUT_DURATION_SECONDS));
            }
            return new LoginAttempt(newCount, null);
        });
    }

    public void resetFailedAttempts(String key) {
        if (key != null) {
            loginAttempts.remove(key.toLowerCase().trim());
        }
    }

    public String createPasswordResetToken(int userId, String email) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        String token = sb.toString();
        resetTokens.put(token, new ResetTokenData(userId, email, Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS)));
        return token;
    }

    public ResetTokenData validateResetToken(String token) {
        if (token == null || token.isBlank()) return null;
        ResetTokenData data = resetTokens.get(token.trim());
        if (data == null) return null;
        if (Instant.now().isAfter(data.expiresAt())) {
            resetTokens.remove(token.trim());
            return null;
        }
        return data;
    }

    public void invalidateResetToken(String token) {
        if (token != null) {
            resetTokens.remove(token.trim());
        }
    }
}
