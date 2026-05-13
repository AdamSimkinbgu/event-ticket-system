package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to ITicketIssuer.issue() (UC-34).
public record IssuanceRequestDTO(
    int buyerUserId,
    String buyerEmail,
    List<TicketIssuanceItemDTO> items
) {
    public record TicketIssuanceItemDTO(
        int eventId,
        String eventName,
        int zoneId,
        String seatNumber                // nullable for standing-zone tickets
    ) {}
}
