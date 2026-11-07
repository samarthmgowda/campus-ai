package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT Service for HS256 tokens (jjwt 0.12.x API).
 *
 * Token subject = auth account id (User.id). Claims carry the role and the
 * role-specific profileId so the filter can rebuild a {@link CurrentUser}
 * without a database round-trip on every request.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:campus-ai-super-secret-key-at-least-32-chars-long}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("email", user.getEmail());
        if (user.getProfileId() != null) {
            claims.put("profileId", user.getProfileId());
        }

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /** Parsed claims for a valid token; throws when invalid or expired. */
    public io.jsonwebtoken.Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
