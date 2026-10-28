package com.ridehailing.identityservice.api;

import com.ridehailing.identityservice.domain.User;
import com.ridehailing.identityservice.service.AuthService;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AuthService auth;

    public UserController(AuthService auth) {
        this.auth = auth;
    }

    public record UpdateProfileRequest(String displayName) {}

    @GetMapping("/{userId}")
    public Map<String, Object> get(@PathVariable UUID userId) {
        User u = auth.requireUser(userId);
        return toMap(u);
    }

    @PatchMapping("/{userId}")
    public Map<String, Object> update(@PathVariable UUID userId, @RequestBody UpdateProfileRequest body) {
        User u = auth.updateProfile(userId, body.displayName());
        return toMap(u);
    }

    private Map<String, Object> toMap(User u) {
        return Map.of(
                "userId", u.getId().toString(),
                "email", u.getEmail(),
                "role", u.getRole(),
                "status", u.getStatus(),
                "displayName", u.getDisplayName() == null ? "" : u.getDisplayName(),
                "createdAt", u.getCreatedAt().toString()
        );
    }
}
