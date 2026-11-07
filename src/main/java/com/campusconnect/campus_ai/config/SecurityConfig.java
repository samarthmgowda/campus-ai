package com.campusconnect.campus_ai.config;

import com.campusconnect.campus_ai.service.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Set;

/**
 * Production Security Configuration.
 *
 * Gates API endpoints by role and handles the JWT filter chain.
 *
 * Two things are deliberately public and must stay that way:
 *  1. The static SPA bundle (index.html + the hashed /assets/** chunks). The
 *     sign-in page is itself part of that bundle, so if the bundle required a
 *     token the app could never boot.
 *  2. Browser page navigations to client-side routes. A reload or deep link
 *     sends no Authorization header (only our fetch() calls attach the bearer
 *     token), and client-side routing lives in the bundle — so the server's job
 *     for these is to hand back the SPA shell. Role routing is then enforced in
 *     the app, and every data endpoint remains token-gated below.
 *
 * All JSON APIs stay authenticated; the admin/analytics/eval surfaces keep
 * their role gates.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Client-side routes the SPA owns. A GET that asks for HTML on one of these
     * is a page load, not an API call — our fetch() calls never send
     * Accept: text/html, so they fall through to the controllers untouched.
     */
    private static final Set<String> SPA_PAGE_PATHS = Set.of(
            "/", "/home", "/chat", "/search", "/reviews", "/roadmaps", "/referrals",
            "/documents", "/projects", "/events", "/community", "/messages", "/notifications",
            "/mentorship", "/recruiter", "/admin", "/analytics", "/profile", "/settings", "/login",
            "/dashboard", "/dashboard/alumni", "/dashboard/recruiter", "/skills-graph", "/eval",
            "/connect", "/team-requests", "/placement-training"
    );

    private static boolean isSpaPageLoad(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) return false;
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) return false;
        String path = request.getRequestURI();
        // Deep links: /events/{id} and /profiles/{id}.
        return SPA_PAGE_PATHS.contains(path)
                || path.startsWith("/events/")
                || path.startsWith("/profiles/");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/demo/users").permitAll() // Kept for demo picker, should be gated in real prod
                // SPA shell + static bundle. Without these the login page itself fails to load.
                .requestMatchers("/", "/index.html", "/favicon.svg", "/favicon.ico",
                                 "/*.png", "/*.svg", "/assets/**", "/static/**").permitAll()
                // Browser page navigations to client-side routes serve the shell; data APIs stay gated.
                .requestMatchers(SecurityConfig::isSpaPageLoad).permitAll()
                // Role-gated APIs.
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // The Library is an admin surface end to end. Reading the corpus,
                // creating documents, adding chunks (which embed into pgvector) and
                // file upload all mutate or expose the RAG knowledge base, so a
                // student must not reach any of it — not the write paths, and not
                // the list either. This gates DocumentController, DocumentChunkController,
                // DocumentAdminController and DocumentIngestionController together.
                .requestMatchers("/documents/**").hasRole("ADMIN")
                .requestMatchers("/events/admin/**").hasRole("ADMIN")
                .requestMatchers("/reports/**").hasAnyRole("ADMIN", "STUDENT") // 'STUDENT' for their own reports
                .requestMatchers("/eval/**").hasRole("ADMIN")
                // Campus analytics is an admin view; recruiters get their own hub.
                .requestMatchers("/analytics/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
