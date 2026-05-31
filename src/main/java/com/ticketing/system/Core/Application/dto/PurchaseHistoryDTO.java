package com.ticketing.system.Core.Application.dto;

import com.ticketing.system.Core.Domain.Tickets.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

// Output of MemberAccountService.viewMyHistory() (UC-16).
// Shape is reusable for UC-22 (company sales) and UC-31 (admin global) views.
// Immutable per II.3.5.2 — fields reflect the data at time of purchase, not current.
public record PurchaseHistoryDTO(List<PurchaseRecordDTO> records) {
    public record PurchaseRecordDTO(
        int orderReceiptId,
        int eventId,
        String eventName,
        LocalDateTime purchasedAt,
        double totalPaid,
        List<TicketRecordDTO> tickets
    ) {}

    public record TicketRecordDTO(
        int ticketId,
        int zoneId,
        int eventId,
        int orderReceiptId,
        String seatNumber,     // nullable for standing zones
        double pricePaid,
        TicketStatus currentStatus
    ) {}
}
