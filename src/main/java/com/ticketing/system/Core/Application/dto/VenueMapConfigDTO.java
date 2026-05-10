package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to EventManagementService.configureVenueMap() (UC-20).
// On commit, Tickets are pre-generated per the unified-Ticket model.
public record VenueMapConfigDTO(
    String eventId,
    String venueName,
    List<ZoneConfigDTO> zones
) {
    // One zone definition. Either seated (with seat layout) or standing (with capacity).
    public record ZoneConfigDTO(
        String zoneName,
        boolean seated,
        Integer standingCapacity,        // nullable — only for standing zones
        List<String> seatNumbers,        // nullable — only for seated zones, e.g. ["A1","A2","B1",...]
        double pricePerTicket
    ) {}
}
