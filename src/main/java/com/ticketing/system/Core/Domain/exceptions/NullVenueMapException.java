package com.ticketing.system.Core.Domain.exceptions;

/**
 * Specific subclass of {@link EntityNotFoundException} thrown when an event has
 * no associated VenueMap. Signals that seating/zone data is missing for the
 * event rather than that the event itself is absent.
 */
public class NullVenueMapException extends EntityNotFoundException {

    /**
     * @param eventId the event whose venue map is missing
     */
    public NullVenueMapException(Object eventId) {
        super("Venue map not found for event: ", eventId);
    }

}
