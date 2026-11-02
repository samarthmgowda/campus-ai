package com.campusconnect.campus_ai.service;

/**
 * Parsed form of the X-Demo-User header.
 *
 * The header carries a composite identity so that Student, Alumni and
 * Recruiter tables — which all use independent auto-increment ID sequences —
 * can be told apart: {@code "role:id"} such as {@code "student:1"} or
 * {@code "alumni:3"}.
 *
 * A bare number ({@code "1"}) is still accepted and treated as a student, so
 * older clients and the existing test suite keep working. Everything that only
 * ever wanted a student id can call {@link #parseId(String)} /
 * {@link #requireId(String)} and stay oblivious to roles.
 */
public final class DemoIdentity {

    public static final String ROLE_STUDENT = "student";
    public static final String ROLE_ALUMNI = "alumni";
    public static final String ROLE_RECRUITER = "recruiter";

    private final String role;
    private final Long id;

    private DemoIdentity(String role, Long id) {
        this.role = role;
        this.id = id;
    }

    public String role() {
        return role;
    }

    public Long id() {
        return id;
    }

    public boolean isStudent() {
        return ROLE_STUDENT.equals(role);
    }

    public boolean isAlumni() {
        return ROLE_ALUMNI.equals(role);
    }

    public boolean isRecruiter() {
        return ROLE_RECRUITER.equals(role);
    }

    /**
     * Tolerant parse. Returns {@code null} when the header is absent or
     * malformed — the "no identity" contract the optional-auth endpoints rely on.
     */
    public static DemoIdentity parse(String header) {
        if (header == null || header.isBlank()) return null;

        String value = header.trim();
        String role;
        String idPart;

        int sep = value.indexOf(':');
        if (sep < 0) {
            role = ROLE_STUDENT;
            idPart = value;
        } else {
            role = value.substring(0, sep).trim().toLowerCase();
            idPart = value.substring(sep + 1).trim();
            if (!ROLE_STUDENT.equals(role) && !ROLE_ALUMNI.equals(role) && !ROLE_RECRUITER.equals(role)) {
                return null;
            }
        }

        try {
            return new DemoIdentity(role, Long.valueOf(idPart));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Tolerant parse to just the id; {@code null} when absent or malformed. */
    public static Long parseId(String header) {
        DemoIdentity identity = parse(header);
        return identity == null ? null : identity.id;
    }

    /**
     * Strict parse to just the id, for endpoints where identity is required.
     *
     * @throws IllegalArgumentException when the header is missing or malformed
     */
    public static Long requireId(String header) {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("X-Demo-User header required");
        }
        DemoIdentity identity = parse(header);
        if (identity == null) {
            throw new IllegalArgumentException("Invalid X-Demo-User header");
        }
        return identity.id;
    }

    /** Strict parse to the full identity, for endpoints that must know the role. */
    public static DemoIdentity require(String header) {
        if (header == null || header.isBlank()) {
            throw new IllegalArgumentException("X-Demo-User header required");
        }
        DemoIdentity identity = parse(header);
        if (identity == null) {
            throw new IllegalArgumentException("Invalid X-Demo-User header");
        }
        return identity;
    }
}
