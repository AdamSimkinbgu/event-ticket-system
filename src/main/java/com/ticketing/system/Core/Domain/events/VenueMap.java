package com.ticketing.system.Core.Domain.events;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class VenueMap {
    private int id;
    private Location location;
   private final List<InventoryZone> inventoryZones;

    public VenueMap(int id, Location location, List<InventoryZone> inventoryZones) {
    if (inventoryZones == null) {
        throw new IllegalArgumentException("Inventory zones cannot be null");
    }

    this.id = id;
    this.location = location;
    this.inventoryZones = new ArrayList<>(inventoryZones);
}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

      public List<InventoryZone> getInventoryZones() {
        return List.copyOf(inventoryZones);
    }

   

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public InventoryZone getZone(int zoneId) {
        for (InventoryZone zone : inventoryZones) {
            if (zone.getId() == zoneId) {
                return zone;
            }
        }
        throw new IllegalArgumentException("Zone not found");
    }
    
        public boolean checkAvailability(int zoneId, int quantity) {
        InventoryZone zone = getZone(zoneId);
        return zone.checkAvailability(quantity);
    }

    public void releaseTicketsToInventory(Map<Integer, Integer> ticketsByZone) {
        for (Map.Entry<Integer, Integer> entry : ticketsByZone.entrySet()) {
            int zoneId = entry.getKey();
            int quantity = entry.getValue();

            InventoryZone zone = getZone(zoneId);
            zone.release(quantity);
        }
    }

     public void updateZoneCapacity(int zoneId, int newCapacity) {
        InventoryZone zone = getZone(zoneId);
        zone.setCapacity(newCapacity);
    }
}