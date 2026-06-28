package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when optimistic locking detects a concurrent modification on a Ticket /
 * aggregate. The service layer should catch and retry, per lecture 2's
 * optimistic-locking pattern.
 */
public class ConcurrentReservationException extends DomainException {

    /**
     * @param aggregateId the id of the aggregate that was concurrently modified
     */
    public ConcurrentReservationException(Object aggregateId) {
        super("Concurrent modification detected on aggregate: " + aggregateId);
    }

    /**
     * @param message custom detail message
     * @param cause   the underlying optimistic-locking failure
     */
    public ConcurrentReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}
