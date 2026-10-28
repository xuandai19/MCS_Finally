package com.ridehailing.identityservice.service;

import com.ridehailing.identityservice.domain.RefreshToken;
import com.ridehailing.identityservice.domain.User;
import com.ridehailing.identityservice.repository.RefreshTokenRepository;
import com.ridehailing.identityservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AuthService {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "CUSTOMER", "DRIVER", "REVIEWER", "SUPPORT_STAFF", "FINANCE_STAFF", "ADMINISTRATOR");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();
    /** Simple in-memory login attempt limiter (FR-IAM-006). Key = email. */
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public AuthService(UserRepository u, RefreshTokenRepository r, PasswordEncoder e, JwtService j) {
        this.users = u;
        this.refreshTokens = r;
        this.encoder = e;
        this.jwt = j;
    }

    @Transactional
    public Map<String, Object> register(String email, String password, String role, String displayName) {
        String normalized = email.trim().toLowerCase();
        if (users.findByEmail(normalized).isPresent()) {
            throw new IllegalArgumentException("Unable to register with supplied details");
        }
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("Password must contain at least 10 characters");
        }
        String r = role == null || role.isBlank() ? "CUSTOMER" : role.trim().toUpperCase();
        if (!ALLOWED_ROLES.contains(r)) {
            throw new IllegalArgumentException("Unsupported role");
        }
        User user = new User(normalized, encoder.encode(password), r);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        users.save(user);
        return issue(user);
    }

    @Transactional
    public Map<String, Object> login(String email, String password) {
        String normalized = email.trim().toLowerCase();
        if (isLocked(normalized)) {
            throw new IllegalArgumentException("Too many failed attempts. Try again later");
        }
        User user = users.findByEmail(normalized).orElse(null);
        if (user == null || !encoder.matches(password, user.getPasswordHash()) || !"ACTIVE".equals(user.getStatus())) {
            recordFailure(normalized);
            throw new IllegalArgumentException("Invalid credentials");
        }
        attempts.remove(normalized);
        return issue(user);
    }

    private Map<String, Object> issue(User user) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), hash(refresh), Instant.now().plusSeconds(2592000)));
        return Map.of(
                "accessToken", jwt.issue(user.getId(), user.getEmail(), user.getRole()),
                "refreshToken", refresh,
                "tokenType", "Bearer",
                "expiresIn", 900,
                "userId", user.getId().toString(),
                "role", user.getRole()
        );
    }

    @Transactional
    public Map<String, Object> refresh(String token) {
        RefreshToken old = refreshTokens.findByTokenHash(hash(token))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (!old.isActive(Instant.now())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        old.revoke();
        User user = users.findById(old.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        return issue(user);
    }

    @Transactional
    public void logout(String token) {
        refreshTokens.findByTokenHash(hash(token)).ifPresent(RefreshToken::revoke);
    }

    public User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public User updateProfile(UUID id, String displayName) {
        User u = requireUser(id);
        if (displayName != null) {
            u.setDisplayName(displayName.isBlank() ? null : displayName.trim());
        }
        return users.save(u);
    }

    private boolean isLocked(String email) {
        Attempt a = attempts.get(email);
        if (a == null) return false;
        if (a.windowStart + 300_000 < System.currentTimeMillis()) {
            attempts.remove(email);
            return false;
        }
        return a.count.get() >= 5;
    }

    private void recordFailure(String email) {
        attempts.compute(email, (k, v) -> {
            long now = System.currentTimeMillis();
            if (v == null || v.windowStart + 300_000 < now) {
                return new Attempt(now);
            }
            v.count.incrementAndGet();
            return v;
        });
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Attempt {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger(1);
        Attempt(long windowStart) { this.windowStart = windowStart; }
    }
}
