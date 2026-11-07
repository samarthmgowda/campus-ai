package com.campusconnect.campus_ai.entity.enums;

public enum UserRole {
    STUDENT,
    ALUMNI,
    RECRUITER,
    ADMIN;

    /** Lowercase token used by the frontend ("student", "alumni", ...). */
    public String token() {
        return name().toLowerCase();
    }

    /** The STUDENT/ALUMNI/RECRUITER profile row this role resolves against, or null for ADMIN. */
    public UserRole profileRole() {
        return this == ADMIN ? null : this;
    }
}
