package com.ticketing.system.Core.Application.interfaces;

import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.dto.RefundResultDTO;

/**
 * Port for external payment processing. Multi-provider support (I.3.2) is
 * realized in Infrastructure by injecting {@code List<IPaymentGateway>}; this
 * interface stays single. Implementations live in {@code Infrastructure/external/}.
 */
public interface IPaymentGateway {

    /**
     * Identifies the gateway implementation (e.g. "stripe", "paypal"). Used for
     * logging, routing and reporting.
     *
     * @return the gateway's stable identifier
     */
    String getId();

    /**
     * UC-1 / I.1.2 startup verification.
     *
     * @return {@code true} if the gateway is reachable and authenticated
     */
    boolean verifyConnection();

    /**
     * UC-33 — process a charge.
     *
     * @param request the charge details (amount, payment instrument, idempotency key)
     * @return the charge result, including the payment transaction id
     * @throws com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException on failure or decline
     */
    PaymentResultDTO charge(PaymentRequestDTO request);

    /**
     * UC-4 / I.3.3 — refund an existing charge.
     *
     * @param paymentTransactionId the id of the original charge to refund
     * @param amount               the amount to refund
     * @return the refund result
     * @throws com.ticketing.system.Core.Domain.exceptions.RefundFailedException on failure
     */
    RefundResultDTO refund(int paymentTransactionId, double amount);
}
