package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a Ticket cannot be reserved because its current state is not
 * AVAILABLE. SLR.1.2 race-condition prevention — used by ReservationService
 * (UC-9).
 */
public class TicketNotAvailableException extends DomainException {

    /**
     * @param ticketId the ticket that is not available
     */
    public TicketNotAvailableException(Object ticketId) {
        super("Ticket not available: " + ticketId);
    }

    /**
     * @param message custom detail message
     */
    public TicketNotAvailableException(String message) {
        super(message);
    }
}
