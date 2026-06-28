package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an auto-refund itself fails (gateway down for the refund call).
 * Distinct from {@link PaymentGatewayException}: this is the <em>refund</em>
 * side, which has elevated SLR.5 fault-tolerance significance — a customer was
 * charged but not refunded. UC-4. Listeners should escalate (logging, admin
 * alert, retry queue).
 */
public class RefundFailedException extends DomainException {

    /**
     * @param orderReceiptId the order whose refund failed
     * @param reason         why the refund failed
     */
    public RefundFailedException(Object orderReceiptId, String reason) {
        super("Refund failed for order " + orderReceiptId + ": " + reason);
    }

    /**
     * @param orderReceiptId the order whose refund failed
     * @param reason         why the refund failed
     * @param cause          the underlying gateway/transport error
     */
    public RefundFailedException(Object orderReceiptId, String reason, Throwable cause) {
        super("Refund failed for order " + orderReceiptId + ": " + reason, cause);
    }
}
