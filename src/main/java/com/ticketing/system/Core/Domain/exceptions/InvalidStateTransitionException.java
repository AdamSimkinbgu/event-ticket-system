package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an aggregate's state machine rejects a requested transition.
 * Examples: Ticket REFUNDED → RESERVED (illegal); Event COMPLETED → ON_SALE
 * (illegal). Generic — used by Ticket, Event, ActiveOrder, CompanyAppointment,
 * Notification, etc.
 */
public class InvalidStateTransitionException extends DomainException {

    /**
     * @param entityType the aggregate type whose transition was rejected
     * @param fromState  the current state
     * @param toState    the requested (illegal) target state
     */
    public InvalidStateTransitionException(String entityType, String fromState, String toState) {
        super(entityType + " cannot transition from " + fromState + " to " + toState);
    }

    /**
     * @param message custom detail message
     */
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
