package com.ticketing.system.Core.Application.dto;

import java.util.List;

import com.ticketing.system.Core.Domain.events.InventoryZone;

// Output of CatalogService.getEventVenueMap() (UC-8).
public class VenueMapDTO {
    private int id;
    private List<InventoryZoneDTO> inventoryZones;

    public VenueMapDTO(int id, List<InventoryZoneDTO> inventoryZones) {
        this.id = id;
        this.inventoryZones = inventoryZones;
    }

    public int getId() {
        return id;
    }

    public List<InventoryZoneDTO> getInventoryZones() {
        return inventoryZones;
    }

    
}
