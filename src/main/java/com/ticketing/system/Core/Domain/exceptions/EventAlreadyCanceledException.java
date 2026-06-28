package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an operation is attempted on an already-CANCELED event. Distinct
 * from {@link InvalidStateTransitionException} — it gives a clearer error.
 * UC-19, UC-9, UC-10.
 */
public class EventAlreadyCanceledException extends DomainException {

    /**
     * @param eventId the id of the already-canceled event
     */
    public EventAlreadyCanceledException(Object eventId) {
        super("Event " + eventId + " is already canceled");
    }
}
