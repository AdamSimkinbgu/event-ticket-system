package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;

// Output of ReservationService.reserve() (UC-5/9).
// Returns the lock outcome plus the resulting cart state so the UI doesn't need a second roundtrip.
public class ReservationResultDTO {

    private final int eventId;
    private final int zoneId;
    private final int quantity;
    private final LocalDateTime reservationExpiresAt;

    public ReservationResultDTO(int eventId, int zoneId, int quantity, LocalDateTime reservationExpiresAt) {
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.quantity = quantity;
        this.reservationExpiresAt = reservationExpiresAt;
    }

    public int getEventId() {
        return eventId;
    }

    public int getZoneId() {
        return zoneId;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getReservationExpiresAt() {
        return reservationExpiresAt;
    }
}
