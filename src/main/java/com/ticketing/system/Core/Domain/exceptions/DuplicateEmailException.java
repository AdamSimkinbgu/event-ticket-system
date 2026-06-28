package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when registration attempts to use an email already on record. UC-11.
 */
public class DuplicateEmailException extends DomainException {

    /**
     * @param email the email that is already registered
     */
    public DuplicateEmailException(String email) {
        super("Email already registered: " + email);
    }
}
