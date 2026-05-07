package com.ticketing.system.Core.Domain.events;

import java.util.List;
import java.util.Map;

public class VenueMap {
    private String id;
    private List<InventoryZone> inventoryZones;

    public VenueMap(String id, List<InventoryZone> inventoryZones) {
        this.id = id;
        this.inventoryZones = inventoryZones;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<InventoryZone> getInventoryZones() {
        return inventoryZones;
    }

    public void setInventoryZones(List<InventoryZone> inventoryZones) {
        this.inventoryZones = inventoryZones;
    }


      public InventoryZone getZone(String zoneId) {
    for (InventoryZone zone : inventoryZones) {
        if (zone.getId().equals(zoneId)) {
            return zone;
        }
    }

    throw new IllegalArgumentException("Zone not found");
}
public boolean checkAvailability(String zoneId, int quantity) {
    InventoryZone zone = getZone(zoneId);
     return zone.CheckAvailability(quantity);
}


public void releaseTicketsToInventory(Map<String, Integer> ticketsByZone) {
    for (Map.Entry<String, Integer> entry : ticketsByZone.entrySet()) {
        String zoneId = entry.getKey();
        int quantity = entry.getValue();

        InventoryZone zone = getZone(zoneId);
        zone.release(quantity);
    }
}

}