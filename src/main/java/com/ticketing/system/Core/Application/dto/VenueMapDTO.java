package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Output of CatalogService.getEventVenueMap() (UC-8).
// Combines structural map info with real-time per-zone / per-seat availability.
public record VenueMapDTO(
    String eventId,
    String venueName,
    List<ZoneAvailabilityDTO> zones
) {
    // Per-zone availability snapshot.
    // For seated zones, 'seats' is populated and 'availableCount' equals seats.size() filtered by AVAILABLE.
    // For standing zones, 'seats' is empty and 'availableCount' is the count of available capacity slots.
    public record ZoneAvailabilityDTO(
        String zoneId,
        String zoneName,
        boolean seated,
        int totalCapacity,
        int availableCount,
        List<SeatStatusDTO> seats
    ) {}

    // Per-seat status for seated zones (II.2.2.2).
    public record SeatStatusDTO(
        String ticketId,
        String seatNumber,
        boolean available
    ) {}
}
