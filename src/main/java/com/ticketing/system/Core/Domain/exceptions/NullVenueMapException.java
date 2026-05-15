package com.ticketing.system.Core.Domain.exceptions;

public class NullVenueMapException extends EntityNotFoundException {
    public NullVenueMapException(Object eventId) {
        super("Venue map not found for event: ", eventId);
    }

}
