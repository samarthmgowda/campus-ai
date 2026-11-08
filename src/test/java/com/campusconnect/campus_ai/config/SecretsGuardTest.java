package com.campusconnect.campus_ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the fail-closed contract of {@link SecretsGuard}.
 *
 * These are plain unit tests (no Spring context): the guard's only inputs are an
 * {@link org.springframework.core.env.Environment} and the two resolved
 * properties, so we can construct it directly and assert on startup behaviour.
 */
class SecretsGuardTest {

    /** A realistic, non-placeholder secret of sufficient length. */
    private static final String GOOD_SECRET =
            "9f2c7a1e4b8d0356af11c2d9e7b4a6083f1d5c92e0a7b3648c5d1f2e9a0b7c43";

    private static MockEnvironment envWith(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void startsWithGoodSecretOutsideDev() {
        assertDoesNotThrow(() ->
                new SecretsGuard(envWith(), GOOD_SECRET, "").afterPropertiesSet());
    }

    @Test
    void refusesBlankSecretOutsideDev() {
        assertThrows(IllegalStateException.class, () ->
                new SecretsGuard(envWith(), "", "").afterPropertiesSet());
    }

    @Test
    void refusesCommittedPlaceholderSecretOutsideDev() {
        assertThrows(IllegalStateException.class, () ->
                new SecretsGuard(envWith(),
                        "campus-ai-production-secret-key-must-be-32-chars-long", "")
                        .afterPropertiesSet());
    }

    @Test
    void refusesDevThrowawaySecretOutsideDev() {
        // The value committed in application-dev.properties is public, so it must
        // never be accepted as a real secret when the dev profile is off.
        assertThrows(IllegalStateException.class, () ->
                new SecretsGuard(envWith(),
                        "dev-only-insecure-signing-key-change-me-32b", "")
                        .afterPropertiesSet());
    }

    @Test
    void refusesTooShortSecretOutsideDev() {
        assertThrows(IllegalStateException.class, () ->
                new SecretsGuard(envWith(), "short-secret", "").afterPropertiesSet());
    }

    @Test
    void refusesWeakAdminPasswordOutsideDev() {
        assertThrows(IllegalStateException.class, () ->
                new SecretsGuard(envWith(), GOOD_SECRET, "admin123").afterPropertiesSet());
    }

    @Test
    void allowsBlankAdminPasswordOutsideDev() {
        // Blank means "do not bootstrap an admin", which is a valid posture.
        assertDoesNotThrow(() ->
                new SecretsGuard(envWith(), GOOD_SECRET, "").afterPropertiesSet());
    }

    @Test
    void devProfileOptsOutEntirely() {
        // Throwaway values are expected under the dev profile — no exception.
        assertDoesNotThrow(() ->
                new SecretsGuard(envWith("dev"),
                        "dev-only-insecure-signing-key-change-me-32b", "admin123")
                        .afterPropertiesSet());
        // Even a blank secret is tolerated locally; the dev profile is the opt-out.
        assertDoesNotThrow(() ->
                new SecretsGuard(envWith("dev"), "", "").afterPropertiesSet());
    }
}
