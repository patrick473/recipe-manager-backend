package com.example.recipemanager.security;

import com.example.recipemanager.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService} — no Spring context needed, this is a
 * plain generate/parse round trip plus the two failure modes
 * {@code isTokenValid} must swallow into {@code false}: an expired token and
 * a token whose signature has been tampered with.
 */
class JwtServiceTest {

    // Deliberately NOT the shipped default in application.properties — that
    // literal value is reserved below for asserting the fail-fast check, so
    // every other test here uses its own unrelated dummy secret.
    private static final String SECRET = "HG0Mbf5uI25w245dW8nYBxXTcp8PEeD7ipACBBPfJek=";
    private static final String SHIPPED_DEFAULT_SECRET = "qxkgyGHHJVYdT7Sn2rQWxvbK9rFeIwrvLMhOOx6iFd0=";
    private static final long EXPIRATION_MS = 60_000L;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION_MS, new MockEnvironment());

    private static User testUser() {
        return User.builder().id(42L).username("jsmith").password("hash").build();
    }

    @Test
    void generateThenParseRoundTripRecoversUsernameAndUserId() {
        User user = testUser();
        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("jsmith");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void expiredTokenFailsValidation() {
        JwtService shortLivedService = new JwtService(SECRET, -1_000L, new MockEnvironment()); // already-expired window
        String token = shortLivedService.generateToken(testUser());

        assertThat(shortLivedService.isTokenValid(token)).isFalse();
    }

    @Test
    void tamperedSignatureFailsValidation() {
        String token = jwtService.generateToken(testUser());

        // Flip the last character of the signature segment so it no longer
        // matches what re-signing the header+payload would produce.
        int lastDot = token.lastIndexOf('.');
        String signature = token.substring(lastDot + 1);
        char flipped = signature.charAt(0) == 'a' ? 'b' : 'a';
        String tamperedSignature = flipped + signature.substring(1);
        String tamperedToken = token.substring(0, lastDot + 1) + tamperedSignature;

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    void tokenSignedWithADifferentKeyFailsValidation() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "b3RoZXItc2VjcmV0LWtleS1hdC1sZWFzdC0yNTYtYml0cy1sb25nISE="));
        String foreignToken = Jwts.builder()
                .subject("jsmith")
                .claim("userId", 42L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(otherKey)
                .compact();

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void malformedTokenFailsValidation() {
        assertThat(jwtService.isTokenValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void constructorRejectsTheShippedDefaultSecretOutsideDevProfile() {
        assertThatThrownBy(() -> new JwtService(SHIPPED_DEFAULT_SECRET, EXPIRATION_MS, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret is still the shipped default");
    }

    @Test
    void constructorAllowsTheShippedDefaultSecretUnderDevProfile() {
        MockEnvironment devEnvironment = new MockEnvironment();
        devEnvironment.setActiveProfiles("dev");

        JwtService devJwtService = new JwtService(SHIPPED_DEFAULT_SECRET, EXPIRATION_MS, devEnvironment);

        String token = devJwtService.generateToken(testUser());
        assertThat(devJwtService.isTokenValid(token)).isTrue();
    }
}
