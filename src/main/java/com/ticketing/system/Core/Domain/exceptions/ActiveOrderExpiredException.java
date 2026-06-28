package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an operation (checkout, modify, view) is attempted on an
 * {@code ActiveOrder} whose reservation timer has expired.
 * UC-2 / UC-10 / II.2.8.1 / II.3.0.3.
 */
public class ActiveOrderExpiredException extends DomainException {

    /** Creates the exception with the default "tickets released" message. */
    public ActiveOrderExpiredException() {
        super("Active order has expired; tickets have been released to inventory");
    }

    /**
     * @param message custom detail message
     */
    public ActiveOrderExpiredException(String message) {
        super(message);
    }
}
