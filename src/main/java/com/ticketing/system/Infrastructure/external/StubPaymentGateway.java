package com.ticketing.system.Infrastructure.external;

import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Domain.exceptions.IdempotencyConflictException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.exceptions.RefundFailedException;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StubPaymentGateway implements IPaymentGateway {
    //*Note: changed from not implemented to what's below here, can change however wanted though, it's a stub for our needs */
    private static final String GATEWAY_ID = "stub-payment-gateway";

    private final AtomicInteger transactionIds = new AtomicInteger(1);
    private final ConcurrentHashMap<String, PaymentRequestDTO> requestsByIdempotencyKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PaymentResultDTO> chargesByIdempotencyKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PaymentResultDTO> chargesByTransactionId = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return GATEWAY_ID;
    }

    @Override
    public boolean verifyConnection() {
        return true;
    }

    @Override
    public PaymentResultDTO charge(PaymentRequestDTO request) {
        validateChargeRequest(request);

        PaymentRequestDTO existingRequest = requestsByIdempotencyKey.putIfAbsent(request.idempotencyKey(), request);
        if (existingRequest != null) {
            if (!samePaymentRequest(existingRequest, request)) {
                throw new IdempotencyConflictException(request.idempotencyKey());
            }

            return chargesByIdempotencyKey.get(request.idempotencyKey());
        }

        int transactionId = transactionIds.getAndIncrement();

        PaymentResultDTO result = new PaymentResultDTO(
                transactionId,
                GATEWAY_ID,
                request.amount(),
                request.currency(),
                LocalDateTime.now()
        );

        chargesByIdempotencyKey.put(request.idempotencyKey(), result);
        chargesByTransactionId.put(transactionId, result);

        return result;
    }

    @Override
    public RefundResultDTO refund(int paymentTransactionId, double amount) {
        if (paymentTransactionId <= 0) {
            throw new RefundFailedException(paymentTransactionId, "paymentTransactionId must be positive");
        }

        if (amount <= 0) {
            throw new RefundFailedException(paymentTransactionId, "refund amount must be positive");
        }

        PaymentResultDTO originalCharge = chargesByTransactionId.get(paymentTransactionId);
        if (originalCharge == null) {
            throw new RefundFailedException(paymentTransactionId, "original payment transaction not found");
        }

        if (amount > originalCharge.chargedAmount()) {
            throw new RefundFailedException(paymentTransactionId, "cannot refund more than original charge");
        }

        return new RefundResultDTO(
                "refund-" + transactionIds.getAndIncrement(),
                String.valueOf(paymentTransactionId),
                amount,
                LocalDateTime.now(),
                List.of(),
                List.of());
    }

    



    // #################################################################################
    //
    // Helper methods for validating requests and comparing them for idempotency checks.
    //
    // #################################################################################


    private void validateChargeRequest(PaymentRequestDTO request) {
        if (request == null) {
            throw new PaymentGatewayException("payment request must not be null");
        }

        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new PaymentGatewayException("idempotency key is required");
        }

        if (request.amount() <= 0) {
            throw new PaymentGatewayException("payment amount must be positive");
        }

        if (request.currency() == null || request.currency().isBlank()) {
            throw new PaymentGatewayException("currency is required");
        }

        if (request.paymentMethodToken() == null || request.paymentMethodToken().isBlank()) {
            throw new PaymentGatewayException("payment method token is required");
        }

        boolean memberBuyer = request.buyerUserId() != null;
        boolean guestBuyer = request.buyerEmail() != null && !request.buyerEmail().isBlank();

        if (memberBuyer == guestBuyer) {
            throw new PaymentGatewayException("payment request must identify exactly one buyer type: member OR guest");
        }

        if (memberBuyer && request.buyerUserId() <= 0) {
            throw new PaymentGatewayException("buyer user id must be positive");
        }
    }



    private boolean samePaymentRequest(PaymentRequestDTO a, PaymentRequestDTO b) {
        return Double.compare(a.amount(), b.amount()) == 0
                && Objects.equals(a.currency(), b.currency())
                && Objects.equals(a.paymentMethodToken(), b.paymentMethodToken())
                && Objects.equals(a.buyerUserId(), b.buyerUserId())
                && Objects.equals(a.buyerEmail(), b.buyerEmail());
    }
}
