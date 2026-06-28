package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an external payment-gateway call fails (decline, timeout,
 * unavailable). {@code CheckoutService} catches and either retries
 * (timeout/unavailable) or aborts checkout (decline). UC-33; feeds the UC-4
 * refund pipeline if a charge succeeds but issuance fails.
 */
public class PaymentGatewayException extends DomainException {

    /**
     * @param reason the gateway failure detail
     */
    public PaymentGatewayException(String reason) {
        super("Payment gateway failure: " + reason);
    }

    /**
     * @param reason the gateway failure detail
     * @param cause  the underlying transport/gateway error
     */
    public PaymentGatewayException(String reason, Throwable cause) {
        super("Payment gateway failure: " + reason, cause);
    }
}
