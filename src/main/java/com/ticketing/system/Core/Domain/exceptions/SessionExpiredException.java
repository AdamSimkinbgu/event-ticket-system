package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a JWT token is well-formed but past its expiration. Distinct from
 * {@link AuthenticationFailedException} (login flow): this is for an
 * already-issued token failing validation on a subsequent request. UC-12 token
 * validation; consumed by the token-refresh flow.
 */
public class SessionExpiredException extends DomainException {

    /** Creates the exception with the default "session expired" message. */
    public SessionExpiredException() {
        super("Session expired; please re-authenticate");
    }
}
