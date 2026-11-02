package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The composite X-Demo-User header ("role:id") is what lets a student, an
 * alumni and a recruiter — whose tables share auto-increment IDs — be told
 * apart. These cover the tolerant and strict parse contracts.
 */
class DemoIdentityTest {

    // ——— tolerant parse ———

    @Test
    void parsesCompositeStudentId() {
        DemoIdentity identity = DemoIdentity.parse("student:1");
        assertNotNull(identity);
        assertEquals("student", identity.role());
        assertEquals(1L, identity.id());
        assertTrue(identity.isStudent());
    }

    @Test
    void parsesCompositeAlumniId() {
        DemoIdentity identity = DemoIdentity.parse("alumni:3");
        assertNotNull(identity);
        assertEquals("alumni", identity.role());
        assertEquals(3L, identity.id());
        assertTrue(identity.isAlumni());
    }

    @Test
    void parsesCompositeRecruiterId() {
        DemoIdentity identity = DemoIdentity.parse("recruiter:2");
        assertNotNull(identity);
        assertEquals("recruiter", identity.role());
        assertEquals(2L, identity.id());
        assertTrue(identity.isRecruiter());
    }

    /** Role is case-insensitive so hand-written headers keep working. */
    @Test
    void parsesRoleCaseInsensitively() {
        DemoIdentity identity = DemoIdentity.parse("STUDENT:7");
        assertNotNull(identity);
        assertEquals("student", identity.role());
        assertEquals(7L, identity.id());
    }

    @Test
    void toleratesSurroundingWhitespace() {
        DemoIdentity identity = DemoIdentity.parse("  alumni : 4  ");
        assertNotNull(identity);
        assertEquals("alumni", identity.role());
        assertEquals(4L, identity.id());
    }

    /** A bare number is still a student — older clients and tests rely on this. */
    @Test
    void bareNumberDefaultsToStudent() {
        DemoIdentity identity = DemoIdentity.parse("5");
        assertNotNull(identity);
        assertEquals("student", identity.role());
        assertEquals(5L, identity.id());
    }

    @Test
    void absentHeaderIsNull() {
        assertNull(DemoIdentity.parse(null));
        assertNull(DemoIdentity.parse(""));
        assertNull(DemoIdentity.parse("   "));
    }

    @Test
    void malformedHeaderIsNull() {
        assertNull(DemoIdentity.parse("not-a-number"));
        assertNull(DemoIdentity.parse("student:abc"));
        assertNull(DemoIdentity.parse("alumni:"));
        assertNull(DemoIdentity.parse("student:1:2"));
    }

    @Test
    void unknownRoleIsNull() {
        assertNull(DemoIdentity.parse("professor:1"));
    }

    // ——— parseId (tolerant, id-only) ———

    @Test
    void parseIdExtractsIdForEveryRole() {
        assertEquals(1L, DemoIdentity.parseId("student:1"));
        assertEquals(1L, DemoIdentity.parseId("alumni:1"));
        assertEquals(1L, DemoIdentity.parseId("recruiter:1"));
        assertEquals(1L, DemoIdentity.parseId("1"));
    }

    @Test
    void parseIdIsNullWhenAbsentOrMalformed() {
        assertNull(DemoIdentity.parseId(null));
        assertNull(DemoIdentity.parseId("not-a-number"));
        assertNull(DemoIdentity.parseId("professor:1"));
    }

    // ——— requireId / require (strict) ———

    @Test
    void requireIdRejectsMissingHeader() {
        assertThrows(IllegalArgumentException.class, () -> DemoIdentity.requireId(null));
        assertThrows(IllegalArgumentException.class, () -> DemoIdentity.requireId(""));
    }

    @Test
    void requireIdRejectsMalformedHeader() {
        assertThrows(IllegalArgumentException.class, () -> DemoIdentity.requireId("not-a-number"));
    }

    @Test
    void requireIdAcceptsEveryRole() {
        assertEquals(1L, DemoIdentity.requireId("student:1"));
        assertEquals(2L, DemoIdentity.requireId("alumni:2"));
        assertEquals(3L, DemoIdentity.requireId("recruiter:3"));
        assertEquals(4L, DemoIdentity.requireId("4"));
    }

    @Test
    void requireReturnsFullIdentity() {
        DemoIdentity identity = DemoIdentity.require("alumni:9");
        assertEquals("alumni", identity.role());
        assertEquals(9L, identity.id());
    }

    @Test
    void requireRejectsMissingHeader() {
        assertThrows(IllegalArgumentException.class, () -> DemoIdentity.require(null));
        assertThrows(IllegalArgumentException.class, () -> DemoIdentity.require("  "));
    }
}
