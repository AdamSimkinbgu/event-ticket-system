package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Output of CatalogService.getEventVenueMap() (UC-8).
public record VenueMapDTO(
    int venueMapId,
    LocationDTO location,
    List<InventoryZoneDTO> inventoryZones
) {}