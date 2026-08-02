package com.example.recipemanager.security;

import com.example.recipemanager.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Issues and validates the self-signed, HMAC-SHA256 JWTs this API uses for
 * stateless authentication. There is no external issuer/JWK set involved —
 * this service both signs and later verifies its own tokens.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";

    /**
     * The dev-convenience default checked into {@code application.properties}
     * (kept there so a fresh checkout runs with zero config). Anyone who has
     * read this public repo has this value, so a real deployment that never
     * overrides {@code app.jwt.secret} (e.g. via the {@code APP_JWT_SECRET}
     * env var) would let anyone mint valid tokens for any user — see the
     * constructor check below, which refuses to start in that case.
     */
    private static final String SHIPPED_DEFAULT_SECRET = "qxkgyGHHJVYdT7Sn2rQWxvbK9rFeIwrvLMhOOx6iFd0=";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (SHIPPED_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret is still the shipped default — set a real secret before deploying");
        }
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Builds a token whose subject is the account's username and which
     * carries the account id as a custom {@code userId} claim.
     */
    public String generateToken(User user) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + expirationMs);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_USER_ID, user.getId())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object rawUserId = parseClaims(token).get(CLAIM_USER_ID);
        return rawUserId == null ? null : ((Number) rawUserId).longValue();
    }

    /**
     * Validates both the signature and expiry of {@code token}, swallowing
     * any {@link JwtException} (expired, malformed, tampered signature, ...)
     * into {@code false} rather than letting it propagate as a 500 — an
     * invalid token on a protected endpoint should surface as "unauthenticated,"
     * not as a server error.
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
