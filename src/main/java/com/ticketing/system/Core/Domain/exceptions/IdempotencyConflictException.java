package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a payment-gateway idempotency key is reused with different
 * parameters. UC-33 — defends against a double-charge if a retry happens after
 * a partial response.
 */
public class IdempotencyConflictException extends DomainException {

    /**
     * @param idempotencyKey the key that was reused with conflicting parameters
     */
    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key already used with different parameters: " + idempotencyKey);
    }
}
