package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Output of CheckoutService checkout methods (UC-10).
// Returned only on success; failures throw domain exceptions.
public record CheckoutResultDTO(
    double totalCharged,
    int orderReceiptId,
    int paymentTransactionId,
    List<Integer> issuedTicketIds
) {}
