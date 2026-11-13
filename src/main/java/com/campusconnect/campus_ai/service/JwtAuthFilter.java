package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.User;
import com.campusconnect.campus_ai.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates each request from an "Authorization: Bearer <jwt>" header.
 *
 * On a valid token it places a {@link CurrentUser} principal and a single
 * ROLE_<ROLE> authority on the SecurityContext; anonymous requests are left
 * untouched so public endpoints (permitted in SecurityConfig) still serve.
 *
 * The token is only proof of *who signed in*; the authority for *what they may
 * do* is the account row. So every request reloads the account and builds the
 * principal from the database rather than from the claims:
 *
 *  - a token for an account that no longer exists (a wiped development
 *    database, a deleted user) authenticates nobody and yields 401, instead of
 *    a principal pointing at a profile row that is gone;
 *  - a disabled account is refused immediately rather than at token expiry;
 *  - a role or profile change takes effect on the next request instead of
 *    lingering until the token expires a day later.
 *
 * It costs one primary-key lookup per request. That is the price of the token
 * never being able to outlive the account it describes.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                Long userId = Long.valueOf(claims.getSubject());

                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !user.isEnabled()) {
                    // Signed by us, but the account behind it is gone or closed.
                    SecurityContextHolder.clearContext();
                } else {
                    CurrentUser principal = new CurrentUser(
                            user.getId(), user.getProfileId(), user.getRole(), user.getEmail());
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Invalid / expired token: leave the context anonymous and let
                // SecurityConfig decide (public endpoint serves, protected 401s).
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
