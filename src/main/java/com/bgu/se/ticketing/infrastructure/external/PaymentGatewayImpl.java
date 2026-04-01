package com.bgu.se.ticketing.infrastructure.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub implementation of {@link IPaymentGateway}.
 *
 * <p>This class simulates a real payment provider (e.g., Stripe, PayPal).
 * Replace the body of each method with actual HTTP client calls when integrating
 * a real payment provider.
 */
@Component
public class PaymentGatewayImpl implements IPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayImpl.class);

    @Override
    public PaymentResult charge(String orderId, long amount) {
        log.info("Charging {} units for order {}", amount, orderId);
        // TODO: integrate with real payment provider (e.g., Stripe Charges API)
        String transactionId = UUID.randomUUID().toString();
        log.info("Payment successful for order {}. Transaction ID: {}", orderId, transactionId);
        return PaymentResult.success(transactionId);
    }

    @Override
    public PaymentResult refund(String orderId, long amount) {
        log.info("Refunding {} units for order {}", amount, orderId);
        // TODO: integrate with real payment provider refund API
        String transactionId = UUID.randomUUID().toString();
        log.info("Refund successful for order {}. Transaction ID: {}", orderId, transactionId);
        return PaymentResult.success(transactionId);
    }
}
