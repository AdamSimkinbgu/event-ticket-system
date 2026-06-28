package com.ticketing.system.Core.Domain.exceptions;

/**
 * Specific subclass of {@link EntityNotFoundException} for Event lookups. Lets
 * services catch selectively (e.g. UC-19/20/21/22/31).
 */
public class EventNotFoundException extends EntityNotFoundException {

    /**
     * @param eventId the id that was looked up
     */
    public EventNotFoundException(Object eventId) {
        super("Event not found: ", eventId);
    }
}
