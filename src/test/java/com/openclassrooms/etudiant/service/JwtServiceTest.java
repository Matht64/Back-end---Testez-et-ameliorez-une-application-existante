package com.openclassrooms.etudiant.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UNIT TEST: JwtService is a real object, but we build it ourselves with "new".
 * No Spring, no mocks, no database: JwtService only does calculations.
 *
 * In the real application, Spring fills the two private fields (secretKey, jwtExpiration)
 * from application.properties. Without Spring, we fill them ourselves with ReflectionTestUtils.
 */
class JwtServiceTest {

    // The service expects the secret encoded in Base64, and at least 32 bytes long once decoded.
    private static final String SECRET = base64("a-very-long-test-secret-key-for-hs256-1234");
    private static final long ONE_HOUR = 3_600_000L; // in milliseconds

    private JwtService jwtService;
    private UserDetails john;

    @BeforeEach
    void setUp() {
        jwtService = createJwtService(SECRET, ONE_HOUR);
        john = User.withUsername("john").password("hash").authorities("ROLE_USER").build();
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    private static JwtService createJwtService(String secretKey, long expiration) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secretKey);
        ReflectionTestUtils.setField(service, "jwtExpiration", expiration);
        return service;
    }

    // ---------- generateToken / extractUsername ----------

    @Test
    void generateToken_returnsATokenWithThreeParts() {
        String token = jwtService.generateToken(john);

        // A JWT is header.payload.signature
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    void extractUsername_returnsTheUsernameStoredInTheToken() {
        String token = jwtService.generateToken(john);

        assertEquals("john", jwtService.extractUsername(token));
    }

    // ---------- isTokenValid ----------

    @Test
    void isTokenValid_validTokenOfTheSameUser_returnsTrue() {
        String token = jwtService.generateToken(john);

        assertTrue(jwtService.isTokenValid(token, john));
    }

    @Test
    void isTokenValid_tokenOfAnotherUser_returnsFalse() {
        String tokenOfJohn = jwtService.generateToken(john);
        UserDetails mary = User.withUsername("mary").password("hash").authorities("ROLE_USER").build();

        assertFalse(jwtService.isTokenValid(tokenOfJohn, mary));
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtException() {
        // GIVEN: a service whose tokens expire 1 second BEFORE they are created
        JwtService shortLivedService = createJwtService(SECRET, -1000L);
        String expiredToken = shortLivedService.generateToken(john);

        // WHEN + THEN: the library refuses to read an expired token
        // (JwtAuthFilter catches this exception, so the user stays unauthenticated)
        assertThrows(ExpiredJwtException.class, () -> jwtService.isTokenValid(expiredToken, john));
    }

    @Test
    void isTokenValid_tokenSignedWithAnotherSecret_throwsJwtException() {
        // GIVEN: a token created by someone who does not know our secret
        String otherSecret = base64("another-completely-different-secret-key-5678");
        String forgedToken = createJwtService(otherSecret, ONE_HOUR).generateToken(john);

        // WHEN + THEN: the signature does not match our secret, so the token is rejected
        assertThrows(JwtException.class, () -> jwtService.isTokenValid(forgedToken, john));
    }

    @Test
    void extractUsername_malformedToken_throwsJwtException() {
        assertThrows(JwtException.class, () -> jwtService.extractUsername("this-is-not-a-jwt"));
    }

    // ---------- secret key configuration ----------

    @Test
    void generateToken_missingSecretKey_throwsIllegalStateException() {
        JwtService serviceWithoutSecret = createJwtService(null, ONE_HOUR);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> serviceWithoutSecret.generateToken(john));

        assertEquals("JWT secret key is missing: security.jwt.secret-key", exception.getMessage());
    }

    @Test
    void generateToken_secretKeyTooShort_throwsIllegalStateException() {
        // 16 bytes only: HS256 needs at least 32
        JwtService serviceWithShortSecret = createJwtService(base64("short-secret-16b"), ONE_HOUR);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> serviceWithShortSecret.generateToken(john));

        assertTrue(exception.getMessage().contains("at least 32 bytes"));
    }
}