package com.ticketing.system.Core.Domain.exceptions;

// Specific subclass of EntityNotFoundException for Event lookups.
// Lets services catch selectively (e.g. UC-19/20/21/22/31).
public class EventNotFoundException extends EntityNotFoundException {

    public EventNotFoundException(Object eventId) {
        super("Event", eventId);
    }
}
