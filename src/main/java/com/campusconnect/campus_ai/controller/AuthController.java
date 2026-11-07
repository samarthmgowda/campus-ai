package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.User;
import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.service.AuthContext;
import com.campusconnect.campus_ai.service.AuthService;
import com.campusconnect.campus_ai.service.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public auth endpoints.
 *
 * POST /auth/signup -> creates account (student / alumni / recruiter only)
 * POST /auth/login  -> returns JWT
 * GET  /auth/me     -> current account, resolved from the JWT principal
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signUp(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String fullName = (String) body.get("fullName");
        String roleStr = (String) body.get("role");
        Long profileId = asLong(body.get("profileId"));

        if (roleStr == null || roleStr.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        AuthService.AuthResult result = authService.signUp(
                email, password, fullName, UserRole.valueOf(roleStr.toUpperCase()), profileId);
        Map<String, Object> out = session(result);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String password = (String) body.get("password");

        if (email == null || password == null) {
            throw new IllegalArgumentException("Email and password are required");
        }

        return ResponseEntity.ok(session(authService.login(email, password)));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        CurrentUser cu = AuthContext.require();
        User user = authService.me(cu.userId());
        return ResponseEntity.ok(toView(user));
    }

    // ——— helpers ———

    /** Token plus the public account view — what a client needs to start a session. */
    private Map<String, Object> session(AuthService.AuthResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", result.token());
        out.putAll(toView(result.user()));
        return out;
    }

    /** Public view of an account — never exposes the password hash. */
    private Map<String, Object> toView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("email", user.getEmail());
        view.put("fullName", user.getFullName());
        view.put("role", user.getRole().name().toLowerCase());
        view.put("profileId", user.getProfileId());
        view.put("enabled", user.isEnabled());
        return view;
    }

    private Long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
