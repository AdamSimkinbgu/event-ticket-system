package com.ticketing.system.Core.Domain.exceptions;

/**
 * Base for all domain-level exceptions. Application services may catch these and
 * translate them to DTO error responses; never let domain exceptions leak past
 * the application layer.
 */
public abstract class DomainException extends RuntimeException {

    /**
     * @param message human-readable description of the domain violation
     */
    protected DomainException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description of the domain violation
     * @param cause   the underlying cause
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
