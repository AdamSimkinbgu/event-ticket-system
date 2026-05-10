package com.ticketing.system.Core.Domain.exceptions;

// Thrown when registration attempts to use an already-taken username/email. UC-11.
public class DuplicateUsernameException extends DomainException {

    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
