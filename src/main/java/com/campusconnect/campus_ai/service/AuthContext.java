package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.enums.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Single source of identity for the JWT-based auth layer.
 *
 * Every controller and service that needs "who is acting" reads it from here
 * instead of the retired X-Demo-User header. The JwtAuthFilter populates the
 * SecurityContext with a {@link CurrentUser} principal on each request.
 *
 * Contract:
 *   - {@link #get()}       — Optional; empty when the request is anonymous
 *                            (public endpoints such as /ask, /api/agent).
 *   - {@link #require()}   — the acting user, or 401 when anonymous.
 *   - {@link #requireAdmin()} — the acting user, or 403 unless ROLE_ADMIN.
 *   - {@link #id()}        — the acting user's profile id, or 401.
 */
public final class AuthContext {

    private AuthContext() {}

    /** The acting principal, or empty when the request is anonymous. */
    public static Optional<CurrentUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        Object principal = auth.getPrincipal();
        if (principal instanceof CurrentUser cu) return Optional.of(cu);
        return Optional.empty();
    }

    /** The acting principal, or throw 401 when anonymous. */
    public static CurrentUser require() {
        return get().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    /** The acting principal, or throw 403 unless they hold ROLE_ADMIN. */
    public static CurrentUser requireAdmin() {
        CurrentUser cu = require();
        if (!cu.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return cu;
    }

    /** The acting user's profile id (Student/Alumni/Recruiter row), or 401. */
    public static Long id() {
        return require().actingId();
    }

    /** The acting user's profile id, or null when anonymous. */
    public static Long idOrNull() {
        return get().map(CurrentUser::actingId).orElse(null);
    }

    /** The acting user's role, or null when anonymous. */
    public static UserRole role() {
        return get().map(CurrentUser::role).orElse(null);
    }

    /** True when the acting principal is an admin. */
    public static boolean isAdmin() {
        return get().map(CurrentUser::isAdmin).orElse(false);
    }
}
