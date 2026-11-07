package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.enums.UserRole;
import com.campusconnect.campus_ai.service.CurrentUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Test helper for acting as a signed-in user.
 *
 * The app authenticates from a JWT (populated by {@code JwtAuthFilter}). Tests
 * have no real token, so they place the same principal the filter would on the
 * SecurityContext. A request with no caller set is anonymous, exactly like one
 * with no Authorization header.
 */
final class TestAuth {

    private TestAuth() {}

    /** Sign requests in as a STUDENT with the given profile id. */
    static void asStudent(Long profileId) {
        as(UserRole.STUDENT, profileId);
    }

    static void asAlumni(Long profileId) {
        as(UserRole.ALUMNI, profileId);
    }

    static void asRecruiter(Long profileId) {
        as(UserRole.RECRUITER, profileId);
    }

    static void asAdmin() {
        as(UserRole.ADMIN, null);
    }

    /** Clear any acting user, so the next request is anonymous. */
    static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void as(UserRole role, Long profileId) {
        CurrentUser cu = new CurrentUser(1L, profileId, role, role.token() + "@test.local");
        var auth = new UsernamePasswordAuthenticationToken(
                cu, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
