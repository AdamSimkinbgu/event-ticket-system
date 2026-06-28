package com.ticketing.system.Core.Domain.exceptions;

/**
 * Specific subclass of {@link EntityNotFoundException} for User lookups.
 * UC-12, UC-13, UC-16, UC-23/24, UC-31, etc.
 */
public class UserNotFoundException extends EntityNotFoundException {

    /**
     * @param userId the id that was looked up
     */
    public UserNotFoundException(Object userId) {
        super("User", userId);
    }
}
