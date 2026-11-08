package com.campusconnect.campus_ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the guard aborts the Spring context (not merely throws in isolation),
 * without needing a database.
 *
 * {@link ApplicationContextRunner} refreshes a real context containing only
 * {@link SecretsGuard}, so a rejection surfaces as a startup failure — the same
 * thing an operator would see. This is the end-to-end half of the contract; the
 * unit-level matrix lives in {@link SecretsGuardTest}.
 */
class SecretsGuardContextTest {

    private static final String GOOD_SECRET =
            "9f2c7a1e4b8d0356af11c2d9e7b4a6083f1d5c92e0a7b3648c5d1f2e9a0b7c43";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SecretsGuard.class);

    @Test
    void contextStartsWithAStrongSecret() {
        runner.withPropertyValues("jwt.secret=" + GOOD_SECRET)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SecretsGuard.class);
                    assertThat(ctx).hasNotFailed();
                });
    }

    @Test
    void contextFailsToStartWithPlaceholderSecret() {
        runner.withPropertyValues(
                        "jwt.secret=campus-ai-production-secret-key-must-be-32-chars-long")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("refusing to start");
                });
    }

    @Test
    void contextFailsToStartWithNoSecret() {
        // No jwt.secret property at all — blank default from application.properties.
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasMessageContaining("refusing to start");
        });
    }

    @Test
    void devProfileContextStartsDespiteThrowawaySecrets() {
        runner.withPropertyValues(
                        "jwt.secret=dev-only-insecure-signing-key-change-me-32b",
                        "app.admin.password=admin123")
                .withPropertyValues("spring.profiles.active=dev")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(SecretsGuard.class);
                });
    }
}
