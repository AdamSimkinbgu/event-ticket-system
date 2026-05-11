package com.ticketing.system.unit.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;
import com.ticketing.system.Infrastructure.security.JwtSessionManager;

class JwtSessionManagerTest {

    private static final String SECRET =
        "unit-test-secret-must-be-long-enough-for-hmac-sha-256-please-ignore-me";

    private JwtSessionManager manager;

    @BeforeEach
    void setUp() {
        manager = new JwtSessionManager(SECRET, 60);
    }

    @Test
    void generateToken_producesDottedJwtString() {
        String token = manager.generateToken(42, "alice");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "JWT should have 3 dot-separated parts");
    }

    @Test
    void validateToken_returnsTrueForFreshToken() {
        String token = manager.generateToken(42, "alice");
        assertTrue(manager.validateToken(token));
    }

    @Test
    void validateToken_returnsFalseForNull() {
        assertFalse(manager.validateToken(null));
    }

    @Test
    void validateToken_returnsFalseForBlank() {
        assertFalse(manager.validateToken("   "));
    }

    @Test
    void validateToken_throwsInvalidTokenForGarbage() {
        assertThrows(InvalidTokenException.class, () -> manager.validateToken("not.a.jwt"));
    }

    @Test
    void validateToken_throwsInvalidTokenForTamperedSignature() {
        String token = manager.generateToken(42, "alice");
        // Append extra bytes to the signature segment so HMAC verification fails.
        String tampered = token + "AAAA";
        assertThrows(InvalidTokenException.class, () -> manager.validateToken(tampered));
    }

    @Test
    void validateToken_throwsSessionExpiredForExpiredToken() {
        // Negative expiration → token is issued already past its exp claim.
        JwtSessionManager expiredManager = new JwtSessionManager(SECRET, -1);
        String token = expiredManager.generateToken(42, "alice");
        assertThrows(SessionExpiredException.class, () -> expiredManager.validateToken(token));
    }

    @Test
    void extractUserId_returnsEncodedUserId() {
        String token = manager.generateToken(42, "alice");
        assertEquals(42, manager.extractUserId(token));
    }

    @Test
    void extractUsername_returnsEncodedUsername() {
        String token = manager.generateToken(42, "alice");
        assertEquals("alice", manager.extractUsername(token));
    }

    @Test
    void extractExpiration_returnsFutureEpochMillis() {
        long before = System.currentTimeMillis();
        String token = manager.generateToken(42, "alice");
        long expiry = manager.extractExpiration(token);
        assertTrue(expiry > before, "expiry should be after issuance time");
    }

    @Test
    void isExpired_returnsFalseForFreshToken() {
        String token = manager.generateToken(42, "alice");
        assertFalse(manager.isExpired(token));
    }

    @Test
    void isExpired_returnsTrueForGarbage() {
        assertTrue(manager.isExpired("not.a.jwt"));
    }

    @Test
    void tryExtractUserId_returnsPresentForValidToken() {
        String token = manager.generateToken(42, "alice");
        Optional<Integer> id = manager.tryExtractUserId(token);
        assertTrue(id.isPresent());
        assertEquals(42, id.get());
    }

    @Test
    void tryExtractUserId_returnsEmptyForGarbage() {
        assertTrue(manager.tryExtractUserId("not.a.jwt").isEmpty());
    }
}
