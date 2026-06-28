package com.ticketing.system.Core.Application.interfaces;

/**
 * Port for password hashing. Implemented in Infrastructure by
 * {@code BcryptPasswordHasher} (lecture 2's recommended BCrypt approach). Used by
 * {@code AuthenticationService}.
 */
public interface IPasswordHasher {

    /**
     * UC-11 — hash a raw password before persisting.
     *
     * @param rawPassword the plaintext password
     * @return the hash to store
     */
    String hash(String rawPassword);

    /**
     * UC-12 — verify a raw password against a stored hash.
     *
     * @param rawPassword the plaintext password to check
     * @param storedHash  the previously stored hash
     * @return {@code true} if the password matches the hash
     */
    boolean matches(String rawPassword, String storedHash);
}
