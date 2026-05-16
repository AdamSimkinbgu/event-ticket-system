package com.ticketing.system.Core.Application.dto;

// Input to IPaymentGateway.charge() (UC-33).
// 'idempotencyKey' is critical — if the gateway response is lost, retry must not double-charge.
//
// buyerUserId / buyerEmail are dual identity for Member vs Guest purchases:
//   - Member: buyerUserId set, buyerEmail optional.
//   - Guest:  buyerUserId null, buyerEmail required (D5 reversed — Guests can check out).
public record PaymentRequestDTO(
    String idempotencyKey,
    double amount,
    String currency,
    String paymentMethodToken,           // tokenized card ref — never raw PAN
    Integer buyerUserId,
    String buyerEmail
) {}
