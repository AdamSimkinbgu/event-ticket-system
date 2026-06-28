package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown during UC-1 (I.1.2 / I.1.3) and UC-32 (I.2.2) when no external payment
 * or ticket-issuance service is reachable, so the platform cannot be brought up
 * / the market cannot be opened. Maps to the WSEP {@code handshake} action
 * returning non-OK.
 */
public class ExternalServiceUnavailableException extends DomainException {

    /**
     * @param reason which service is unavailable and why
     */
    public ExternalServiceUnavailableException(String reason) {
        super("Required external service unavailable: " + reason);
    }
}
