package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;

// Output of ReservationService.reserve() (UC-5/9).
// Returns the lock outcome plus the resulting cart state so the UI doesn't need a second roundtrip.
public record ReservationResultDTO(
        int eventId,
        int zoneId,
        int quantity,
        LocalDateTime reservationExpiresAt) {
}
