package com.campusconnect.campus_ai.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * SPA fallback that cannot collide with API routes.
 *
 * The old SpaController used @GetMapping for paths like /notifications,
 * /documents and /events — the same paths the @RestController APIs serve —
 * causing "Ambiguous handler methods mapped" at request time.
 *
 * This filter only forwards real browser page loads (GET + Accept
 * contains text/html) to /index.html. API fetch() calls send
 * Accept: star-slash-star, so they fall through to the controllers untouched.
 * POST/PUT/DELETE are never forwarded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SpaForwardFilter extends OncePerRequestFilter {

    private static final Set<String> SPA_PATHS = Set.of(
            "/home",
            "/chat",
            "/search",
            "/reviews",
            "/roadmaps",
            "/referrals",
            "/documents",
            "/projects",
            "/events",
            "/community",
            "/messages",
            "/notifications",
            "/mentorship",
            "/recruiter",
            "/admin",
            "/analytics",
            "/profile",
            "/settings",
            "/login"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String accept = request.getHeader("Accept");
        if ("GET".equalsIgnoreCase(request.getMethod())
                && SPA_PATHS.contains(path)
                && accept != null && accept.contains("text/html")) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }
        chain.doFilter(request, response);
    }
}
