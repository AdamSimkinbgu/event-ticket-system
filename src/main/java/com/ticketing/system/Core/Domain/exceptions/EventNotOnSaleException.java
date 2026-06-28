package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when reservation/checkout is attempted on an event whose status is not
 * ON_SALE. (DRAFT, SCHEDULED-but-not-yet-published, SOLD_OUT, CANCELED,
 * COMPLETED → all reject.) UC-9, UC-10.
 */
public class EventNotOnSaleException extends DomainException {

    /**
     * @param eventId       the event that is not on sale
     * @param currentStatus the event's current status
     */
    public EventNotOnSaleException(Object eventId, String currentStatus) {
        super("Event " + eventId + " is not on sale (current status: " + currentStatus + ")");
    }
}
