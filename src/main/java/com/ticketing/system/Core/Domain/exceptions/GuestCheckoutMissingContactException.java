package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a Guest attempts to check out without providing the required
 * {@code GuestCheckoutContactDTO} (email + name). D5 (reversed): Guests may
 * check out, but only with valid contact info.
 */
public class GuestCheckoutMissingContactException extends DomainException {

    /**
     * @param reason which contact detail is missing or invalid
     */
    public GuestCheckoutMissingContactException(String reason) {
        super(reason);
    }
}
