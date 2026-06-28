package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a structural correctness constraint (requirements.md §1) is
 * violated by the persisted state — detected by {@code SystemIntegrityVerifier}
 * during UC-1 initialization.
 */
public class SystemIntegrityViolationException extends DomainException {

    /**
     * @param reason which integrity constraint was violated
     */
    public SystemIntegrityViolationException(String reason) {
        super("System integrity violation: " + reason);
    }
}
