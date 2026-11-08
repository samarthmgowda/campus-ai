package com.campusconnect.campus_ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Refuses to start a non-development instance that is configured with a missing
 * or guessable secret.
 *
 * Why this exists: {@code jwt.secret} signs every token. If it is blank, too
 * short, or a value that lives in version control, anyone who can read the repo
 * can forge a token claiming {@code ROLE_ADMIN} against a running instance. The
 * same applies to a known-weak bootstrap admin password. A misconfiguration like
 * that should be a loud startup failure, not a silently insecure deployment.
 *
 * This runs as a {@link InitializingBean}, i.e. during context refresh. That is
 * deliberate: the check fires while singletons are being created, which is
 * before the embedded web server starts accepting connections — so a bad config
 * aborts the process without ever exposing an insecure port.
 *
 * Contract:
 *   - The {@code dev} profile opts out entirely. Local runs stay zero-config;
 *     the throwaway values in {@code application-dev.properties} are expected.
 *   - Outside {@code dev}, a blank / too-short / placeholder {@code jwt.secret}
 *     aborts startup.
 *   - Outside {@code dev}, a non-blank {@code app.admin.password} that equals a
 *     known-weak default aborts startup. A blank password is allowed: it simply
 *     means no admin is bootstrapped (AdminBootstrap skips), which is a valid
 *     posture rather than an insecure one.
 */
@Component
public class SecretsGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SecretsGuard.class);

    /** HS256 needs at least 256 bits / 32 bytes of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Values that have shipped in this repo or are otherwise trivially guessable.
     * Any of these as a live secret is treated as no secret at all.
     */
    private static final Set<String> KNOWN_WEAK = Set.of(
            "campus-ai-production-secret-key-must-be-32-chars-long",
            "campus-ai-super-secret-key-at-least-32-chars-long",
            "dev-only-insecure-signing-key-change-me-32b",
            "change-me",
            "changeme",
            "secret",
            "password"
    );

    private static final Set<String> KNOWN_WEAK_PASSWORDS = Set.of(
            "admin123",
            "admin",
            "password",
            "changeme",
            "change-me"
    );

    private final Environment env;
    private final String jwtSecret;
    private final String adminPassword;

    public SecretsGuard(Environment env,
                        @Value("${jwt.secret:}") String jwtSecret,
                        @Value("${app.admin.password:}") String adminPassword) {
        this.env = env;
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
    }

    private boolean devProfileActive() {
        for (String p : env.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p)) return true;
        }
        return false;
    }

    @Override
    public void afterPropertiesSet() {
        if (devProfileActive()) {
            log.warn("SecretsGuard: 'dev' profile is active — running with throwaway "
                    + "development secrets. Do not deploy this configuration.");
            return;
        }

        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.isEmpty()) {
            fail("jwt.secret is not set.",
                    "Provide the JWT_SECRET environment variable (>= " + MIN_SECRET_BYTES
                            + " random characters), or run locally with the 'dev' profile.");
        } else if (isKnownWeak(secret)) {
            fail("jwt.secret is a known placeholder value.",
                    "This value is in version control and must be treated as compromised. "
                            + "Generate a fresh secret, e.g. `openssl rand -base64 48`.");
        }
        int secretLen = secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretLen < MIN_SECRET_BYTES) {
            fail("jwt.secret is too short (" + secretLen + " bytes; HS256 needs >= "
                            + MIN_SECRET_BYTES + ").",
                    "Generate a longer secret, e.g. `openssl rand -base64 48`.");
        }

        String pw = adminPassword == null ? "" : adminPassword.trim();
        if (!pw.isEmpty() && KNOWN_WEAK_PASSWORDS.contains(pw.toLowerCase())) {
            fail("app.admin.password is a known-weak default.",
                    "Set a strong APP_ADMIN_PASSWORD, or leave it blank to skip admin "
                            + "bootstrapping outside development.");
        }

        log.info("SecretsGuard: secret configuration passed validation.");
    }

    private boolean isKnownWeak(String value) {
        String lower = value.toLowerCase();
        if (KNOWN_WEAK.contains(lower)) return true;
        // Catch near-misses where the weak token is embedded but not an exact match.
        return lower.contains("change-me") || lower.contains("changeme")
                || lower.contains("must-be-32-chars") || lower.contains("insecure-signing-key");
    }

    private void fail(String problem, String remedy) {
        String message = "SECURITY: refusing to start. " + problem + " " + remedy
                + " (set the 'dev' profile only for local development)";
        log.error(message);
        throw new IllegalStateException(message);
    }
}
