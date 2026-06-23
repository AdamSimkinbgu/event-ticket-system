package com.ticketing.system.Infrastructure.external;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.CardDetailsDTO;
import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.exceptions.RefundFailedException;

import lombok.extern.slf4j.Slf4j;

/**
 * Real payment gateway backed by the WSEP external system. Active only when
 * {@code external.payment-gateway=wsep}; otherwise {@link StubPaymentGateway} is
 * used. Each method is one WSEP action exactly per the documented contract:
 * {@code handshake} → {@link #verifyConnection()}, {@code pay} → {@link #charge},
 * {@code refund} → {@link #refund}. {@code "-1"} is the universal failure reply
 * and is translated into the matching domain exception.
 *
 * <p>DEBUG logs are masked: the card number shows only its last 4 and the CVV /
 * expiry / holder are never logged (they are still sent on the wire — WSEP
 * requires them — just never recorded).
 */
@Component
@ConditionalOnProperty(name = "external.payment-gateway", havingValue = "wsep")
@Slf4j
public class WsepPaymentGateway implements IPaymentGateway {

    private static final String GATEWAY_ID = "wsep-payment-gateway";
    private static final String FAILURE = "-1";

    private final WsepHttpClient http;

    public WsepPaymentGateway(WsepHttpClient http) {
        this.http = http;
    }

    @Override
    public String getId() {
        return GATEWAY_ID;
    }

    @Override
    public boolean verifyConnection() {
        try {
            boolean ok = "OK".equalsIgnoreCase(http.post(WsepHttpClient.action("handshake")));
            log.debug("wsep handshake: reachable={}", ok);
            return ok;
        } catch (RuntimeException e) {
            log.debug("wsep handshake failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public PaymentResultDTO charge(PaymentRequestDTO request) {
        CardDetailsDTO card = request.card();
        if (card == null || card.cardNumber() == null || card.cardNumber().isBlank()) {
            throw new PaymentGatewayException("card details are required");
        }

        long start = System.nanoTime();
        log.debug("wsep pay -> amount={} {} card={}",
                request.amount(), request.currency(), SensitiveDataMasker.mask(card.cardNumber()));

        WsepHttpClient.Form form = WsepHttpClient.action("pay")
                .add("amount", request.amount())
                .add("currency", request.currency())
                .add("card_number", card.cardNumber())
                .add("month", card.expiryMonth())
                .add("year", card.expiryYear())
                .add("holder", card.holderName())
                .add("cvv", card.cvv())
                .add("id", request.buyerUserId() != null ? request.buyerUserId() : 0);

        String body;
        try {
            body = http.post(form);
        } catch (WsepCommunicationException e) {
            throw new PaymentGatewayException("payment gateway unreachable: " + e.getMessage());
        }

        if (FAILURE.equals(body)) {
            log.debug("wsep pay declined ({} ms)", msSince(start));
            throw new PaymentGatewayException("payment declined by gateway");
        }

        int transactionId = parsePositiveInt(body, "pay");
        log.debug("wsep pay ok: txnId={} ({} ms)", transactionId, msSince(start));
        return new PaymentResultDTO(
                transactionId,
                GATEWAY_ID,
                request.amount(),
                request.currency(),
                LocalDateTime.now());
    }

    @Override
    public RefundResultDTO refund(int paymentTransactionId, double amount) {
        if (paymentTransactionId <= 0) {
            throw new RefundFailedException(paymentTransactionId, "paymentTransactionId must be positive");
        }

        long start = System.nanoTime();
        log.debug("wsep refund -> txnId={}", paymentTransactionId);

        String body;
        try {
            body = http.post(WsepHttpClient.action("refund").add("transaction_id", paymentTransactionId));
        } catch (WsepCommunicationException e) {
            throw new RefundFailedException(paymentTransactionId, "refund gateway unreachable: " + e.getMessage());
        }

        // WSEP refund reverses the whole charge by id and returns 1/-1 — there is no partial-refund amount.
        if (!"1".equals(body)) {
            log.debug("wsep refund failed: body={} ({} ms)", body, msSince(start));
            throw new RefundFailedException(paymentTransactionId, "refund rejected by gateway");
        }

        log.debug("wsep refund ok: txnId={} ({} ms)", paymentTransactionId, msSince(start));
        return new RefundResultDTO(
                "wsep-refund-" + paymentTransactionId,
                String.valueOf(paymentTransactionId),
                amount,
                LocalDateTime.now(),
                List.of(),
                List.of());
    }

    private int parsePositiveInt(String body, String action) {
        try {
            int value = Integer.parseInt(body == null ? "" : body.trim());
            if (value <= 0) {
                throw new PaymentGatewayException("wsep " + action + " returned non-positive value: " + value);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("wsep " + action + " returned an unparseable response");
        }
    }

    private static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
