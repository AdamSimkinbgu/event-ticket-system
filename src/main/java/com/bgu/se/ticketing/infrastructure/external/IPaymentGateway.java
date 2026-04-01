package com.bgu.se.ticketing.infrastructure.external;

/**
 * Domain interface representing an external payment gateway.
 *
 * <p>Defined here (in infrastructure/external) so the Application layer can
 * reference it without pulling in infrastructure details.
 */
public interface IPaymentGateway {

    /**
     * Charges the given amount for an order.
     *
     * @param orderId the order being paid for
     * @param amount  the amount to charge in minor units (e.g., cents)
     * @return a {@link PaymentResult} indicating success or failure
     */
    PaymentResult charge(String orderId, long amount);

    /**
     * Refunds a previously charged amount for an order.
     *
     * @param orderId the order to refund
     * @param amount  the amount to refund in minor units
     * @return a {@link PaymentResult} indicating success or failure
     */
    PaymentResult refund(String orderId, long amount);
}
