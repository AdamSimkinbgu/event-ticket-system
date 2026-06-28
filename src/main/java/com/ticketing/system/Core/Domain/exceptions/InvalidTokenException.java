package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a JWT token is malformed, has an invalid signature, or otherwise
 * can't be parsed. Distinct from {@link SessionExpiredException} (token was
 * valid, just timed out) and {@link AuthenticationFailedException} (login flow).
 * UC-12 token validation.
 */
public class InvalidTokenException extends DomainException {

    /** Creates the exception with the default "Invalid authentication token" message. */
    public InvalidTokenException() {
        super("Invalid authentication token");
    }

    /**
     * @param reason why the token could not be parsed
     */
    public InvalidTokenException(String reason) {
        super("Invalid authentication token: " + reason);
    }
}
