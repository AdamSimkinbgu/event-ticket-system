package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when the external ticket-issuance service fails after a successful
 * charge. This exception is the trigger for the UC-4 auto-refund flow (I.3.3).
 * UC-34.
 */
public class TicketIssuanceFailedException extends DomainException {

    /**
     * @param reason the issuance failure detail
     */
    public TicketIssuanceFailedException(String reason) {
        super("Ticket issuance failed: " + reason);
    }

    /**
     * @param reason the issuance failure detail
     * @param cause  the underlying service error
     */
    public TicketIssuanceFailedException(String reason, Throwable cause) {
        super("Ticket issuance failed: " + reason, cause);
    }
}
