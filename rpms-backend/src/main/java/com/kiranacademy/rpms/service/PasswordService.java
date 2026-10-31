package com.kiranacademy.rpms.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.kiranacademy.rpms.exception.BadRequestException;

@Service
public class PasswordService {

    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private static final Pattern LETTER_PATTERN = Pattern.compile(".*[a-zA-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");

    public PasswordService() {
        this.bCryptPasswordEncoder = new BCryptPasswordEncoder(10);
    }

    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BadRequestException("Password cannot be empty.");
        }
        return bCryptPasswordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }

        // Standard BCrypt match ($2a$, $2b$, $2y$)
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            return bCryptPasswordEncoder.matches(rawPassword, storedHash);
        }

        // Support legacy demo placeholder for existing mock accounts
        if ("DEMO_HASH_REPLACE_BEFORE_LOGIN".equalsIgnoreCase(storedHash)) {
            return "password".equals(rawPassword) ||
                   "admin123".equals(rawPassword) ||
                   "demo123".equals(rawPassword);
        }

        // Fallback for legacy SHA-256
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
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

        return rawPassword.equals(storedHash);
    }

    public void validatePolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long.");
        }
        if (password.length() > 100) {
            throw new BadRequestException("Password cannot exceed 100 characters.");
        }
        if (!LETTER_PATTERN.matcher(password).matches()) {
            throw new BadRequestException("Password must contain at least one letter.");
        }
        if (!DIGIT_PATTERN.matcher(password).matches()) {
            throw new BadRequestException("Password must contain at least one number.");
        }
    }

    public boolean isLegacyHash(String storedHash) {
        if (storedHash == null) return false;
        return !storedHash.startsWith("$2a$") &&
               !storedHash.startsWith("$2b$") &&
               !storedHash.startsWith("$2y$");
    }
}
