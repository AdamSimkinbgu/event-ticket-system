package com.ticketing.system.Core.Domain.exceptions;

/**
 * Catch-all for invariant violations that don't have a more specific exception.
 * Prefer a specific subclass when one fits — this is for the long tail.
 */
public class BusinessRuleViolationException extends DomainException {

    /**
     * @param message description of the violated business rule
     */
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
