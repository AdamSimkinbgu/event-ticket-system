package com.ticketing.system.Core.Domain.exceptions;

/**
 * Specific subclass of {@link EntityNotFoundException} for Ticket lookups.
 * Differs from {@link TicketNotAvailableException}: 'NotFound' = no such ticket
 * exists; 'NotAvailable' = the ticket exists but its status forbids the
 * operation.
 */
public class TicketNotFoundException extends EntityNotFoundException {

    /**
     * @param ticketId the id that was looked up
     */
    public TicketNotFoundException(Object ticketId) {
        super("Ticket", ticketId);
    }
}
