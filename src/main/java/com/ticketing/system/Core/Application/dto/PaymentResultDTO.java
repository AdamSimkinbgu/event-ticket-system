package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;

// Output of IPaymentGateway.charge() (UC-33).
// Failure = a PaymentGatewayException is thrown by the gateway adapter; this DTO
// only represents successful charges.
public record PaymentResultDTO(
    String paymentTransactionId,
    String gatewayName,
    double chargedAmount,
    String currency,
    LocalDateTime chargedAt
) {}
