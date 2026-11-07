package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.enums.UserRole;

/**
 * The authenticated principal carried on the SecurityContext for every
 * request bearing a valid JWT.
 *
 * @param userId    the auth account id (User.id)
 * @param profileId the role-specific profile row id — Student.id for STUDENT,
 *                  Alumni.id for ALUMNI, Recruiter.id for RECRUITER. This is the
 *                  id every existing service expects when it asks "who is acting".
 *                  Null for an ADMIN that is not bound to a profile row.
 * @param role      the account role
 * @param email     the account email
 */
public record CurrentUser(
        Long userId,
        Long profileId,
        UserRole role,
        String email
) {
    /** Lowercase role token used by the frontend ("student", "alumni", ...). */
    public String roleToken() {
        return role.name().toLowerCase();
    }

    /** True when this principal may perform admin actions. */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * The id acting services should use for "me" queries. Falls back to the
     * account id when no profile row is bound (admin accounts).
     */
    public Long actingId() {
        return profileId != null ? profileId : userId;
    }
}
