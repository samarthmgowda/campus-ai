package com.campusconnect.campus_ai.config;

import com.campusconnect.campus_ai.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Provisions the first ADMIN account at startup from configuration.
 *
 * Self-service sign-up deliberately refuses the ADMIN role, so an operator
 * creates the initial admin here. The seed is idempotent on email: once the
 * admin exists, changing nothing here has no further effect.
 *
 * Set password via env var APP_ADMIN_PASSWORD in any real deployment; the
 * default is a local-dev convenience only.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.full-name:Campus Admin}")
    private String adminFullName;

    @Bean
    public ApplicationRunner adminSeeder(AuthService authService) {
        return (ApplicationArguments args) -> {
            if (adminEmail == null || adminEmail.isBlank()) {
                log.info("No app.admin.email configured — skipping admin bootstrap.");
                return;
            }
            if (adminPassword == null || adminPassword.isBlank()) {
                log.warn("app.admin.email is set but app.admin.password is empty — skipping admin bootstrap.");
                return;
            }
            authService.ensureAdmin(
                    adminEmail.trim().toLowerCase(Locale.ROOT),
                    adminPassword,
                    adminFullName);
            log.info("Admin account ensured for {}", adminEmail);
        };
    }
}
