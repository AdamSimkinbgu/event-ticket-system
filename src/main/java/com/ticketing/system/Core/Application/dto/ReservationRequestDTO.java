package com.ticketing.system.Core.Application.dto;

// Input to ReservationService.reserve() (UC-5 / UC-9).
// Two selection modes (II.2.5):
//   - Specific seat:   ticketId populated, quantity ignored
//   - Quantity-zone:   zoneId + quantity populated, ticketId null
public record ReservationRequestDTO(
    String eventId,
    String zoneId,
    String ticketId,    // nullable — only for specific-seat selection
    Integer quantity    // nullable — only for quantity-zone selection
) {}
