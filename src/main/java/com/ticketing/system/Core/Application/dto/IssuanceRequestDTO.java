package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to ITicketIssuer.issue() (UC-34).
public record IssuanceRequestDTO(
    String orderReceiptId,
    int buyerUserId,
    String buyerEmail,
    List<TicketIssuanceItemDTO> items
) {
    public record TicketIssuanceItemDTO(
        String ticketId,
        String eventId,
        String eventName,
        String zoneId,
        String seatNumber                // nullable for standing-zone tickets
    ) {}
}
