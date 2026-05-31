package com.ticketing.system.Core.Application.dto;

import java.lang.reflect.AccessFlag.Location;
import java.util.List;

import com.ticketing.system.Core.Domain.events.InventoryZone;

// Output of CatalogService.getEventVenueMap() (UC-8).
public record VenueMapDTO(
    int venueMapId,
    LocationDTO location,
    List<InventoryZoneDTO> inventoryZones
) {}