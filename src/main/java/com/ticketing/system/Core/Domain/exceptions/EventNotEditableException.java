package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an Owner attempts to edit an Event/VenueMap/Policy field that is
 * frozen because tickets have already been sold. II.3.5.2 / II.4.5.2
 * immutability. UC-19, UC-20, UC-21.
 */
public class EventNotEditableException extends DomainException {

    /**
     * @param eventId the event whose field is frozen
     * @param field   the name of the field that cannot be edited
     */
    public EventNotEditableException(Object eventId, String field) {
        super("Event " + eventId + " field '" + field + "' is not editable after tickets are sold");
    }

    /**
     * @param message custom detail message
     */
    public EventNotEditableException(String message) {
        super(message);
    }
}
