package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * Immutable audit record for an external transaction related to an OrderReceipt.
 *
 * Do not store PaymentResultDTO / IssuanceResultDTO in the domain. Those are
 * application/external-port DTOs. This value object stores only the stable domain
 * facts needed for purchase history, refunds, and audit.
 */
public final class TransactionRecord implements InvariantChecked {

    public enum TransactionType {
        PAYMENT_CHARGE,    // corresponds to a successful charge via IPaymentGateway. Amount should be positive.
        TICKET_ISSUANCE,   // corresponds to successful ticket issuance via ITicketIssuer. Amount should be zero.
        REFUND             // corresponds to a successful refund via IPaymentGateway. Amount should be positive, representing the refunded amount.
    }

    private final TransactionType type;
    private final String providerName;
    private final String externalTransactionId;
    private final double amount;
    private final String currency;
    private final LocalDateTime timestamp;

    private TransactionRecord(
            TransactionType type,
            String providerName,
            String externalTransactionId,
            double amount,
            String currency,
            LocalDateTime timestamp
    ) {
        this.type = type;
        this.providerName = providerName;
        this.externalTransactionId = externalTransactionId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
        checkInvariants();
    }

    public static TransactionRecord paymentCharge(
            int paymentTransactionId,
            String gatewayName,
            double amount,
            String currency,
            LocalDateTime chargedAt
    ) {
        if (paymentTransactionId <= 0) {
            throw new IllegalArgumentException("paymentTransactionId must be positive");
        }
        return new TransactionRecord(
                TransactionType.PAYMENT_CHARGE,
                gatewayName,
                String.valueOf(paymentTransactionId),
                amount,
                currency,
                chargedAt
        );
    }

    public static TransactionRecord ticketIssuance(
            String issuanceTransactionId,
            String issuerName,
            LocalDateTime issuedAt
    ) {
        return new TransactionRecord(
                TransactionType.TICKET_ISSUANCE,
                issuerName,
                issuanceTransactionId,
                0.0,
                null,
                issuedAt
        );
    }

    public static TransactionRecord refund(
            String refundTransactionId,
            String gatewayName,
            double amount,
            String currency,
            LocalDateTime refundedAt
    ) {
        return new TransactionRecord(
                TransactionType.REFUND,
                gatewayName,
                refundTransactionId,
                amount,
                currency,
                refundedAt
        );
    }

    public TransactionType getType() {
        return type;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public void checkInvariants() {
        if (type == null) {
            throw new IllegalStateException("TransactionRecord invariant violated: type must not be null");
        }
        if (externalTransactionId == null || externalTransactionId.isBlank()) {
            throw new IllegalStateException("TransactionRecord invariant violated: externalTransactionId must not be blank");
        }
        if (amount < 0) {
            throw new IllegalStateException("TransactionRecord invariant violated: amount must be non-negative");
        }
        if ((type == TransactionType.PAYMENT_CHARGE || type == TransactionType.REFUND)
                && (currency == null || currency.isBlank())) {
            throw new IllegalStateException("TransactionRecord invariant violated: payment/refund currency must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalStateException("TransactionRecord invariant violated: timestamp must not be null");
        }
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalStateException("TransactionRecord invariant violated: providerName must not be blank");
        }
    }
}
