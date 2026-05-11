package com.ticketing.system.unit.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Infrastructure.security.BcryptPasswordHasher;

class BcryptPasswordHasherTest {

    private BcryptPasswordHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new BcryptPasswordHasher();
    }

    @Test
    void hash_producesNonNullNonEmptyString() {
        String result = hasher.hash("Password1");
        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    void hash_producesBcryptFormat() {
        String result = hasher.hash("Password1");
        // BCrypt strings start with $2a$, $2b$, or $2y$ followed by the cost factor.
        assertTrue(result.startsWith("$2a$") || result.startsWith("$2b$") || result.startsWith("$2y$"),
            "expected BCrypt format prefix, got: " + result);
    }

    @Test
    void hash_sameInputProducesDifferentOutput() {
        // Each call uses a fresh random salt, so identical inputs hash to different strings.
        String first = hasher.hash("Password1");
        String second = hasher.hash("Password1");
        assertNotEquals(first, second);
    }

    @Test
    void hash_doesNotContainRawPassword() {
        String raw = "Password1";
        String result = hasher.hash(raw);
        assertTrue(!result.contains(raw), "hash should not contain the raw password");
    }
}
