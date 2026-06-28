package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when registration attempts to use an already-taken username. UC-11.
 */
public class DuplicateUsernameException extends DomainException {

    /**
     * @param username the username that is already taken
     */
    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
