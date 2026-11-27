package edu.campus.canteen.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class PasswordAndJwtTest {
    private static final String TEST_SECRET = "test-only-secret-key-with-more-than-32-bytes";

    @Test
    void bcryptShouldEncodeAndMatchPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "test-password-123";

        String encoded = encoder.encode(rawPassword);

        assertNotEquals(rawPassword, encoded);
        assertTrue(encoder.matches(rawPassword, encoded));
        assertFalse(encoder.matches("wrong-password", encoded));
    }

    @Test
    void jwtShouldIssueAndParseUserIdentity() {
        JwtService jwtService = new JwtService(TEST_SECRET);

        String token = jwtService.generateToken(42L, "STUDENT");
        JwtService.TokenClaims claims = jwtService.parseToken(token);

        assertEquals(42L, claims.userId());
        assertEquals("STUDENT", claims.role());
    }

    @Test
    void jwtShouldRejectExpiredToken() {
        var key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String expiredToken = Jwts.builder()
                .subject("42")
                .claim("role", "STUDENT")
                .issuedAt(new Date(now.getTime() - 10_000))
                .expiration(new Date(now.getTime() - 1_000))
                .signWith(key)
                .compact();
        JwtService jwtService = new JwtService(TEST_SECRET);

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseToken(expiredToken));
    }
}
