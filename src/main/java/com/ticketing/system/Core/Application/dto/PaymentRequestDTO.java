package com.ticketing.system.Core.Application.dto;

// Input to IPaymentGateway.charge() (UC-33).
// 'idempotencyKey' is critical — if the gateway response is lost, retry must not double-charge.
public record PaymentRequestDTO(
    String idempotencyKey,
    double amount,
    String currency,
    String paymentMethodToken,           // tokenized card ref — never raw PAN
    int buyerUserId
) {}
