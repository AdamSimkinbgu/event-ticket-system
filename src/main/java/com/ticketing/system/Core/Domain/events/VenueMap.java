package com.ticketing.system.Core.Domain.events;

import java.util.List;

import com.ticketing.system.Core.Domain.events.InventorySelection;

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



    
    //// changed name from reserveTickets to reserveInventory and added InventorySelection parameter to support seated zones as well.
    // supports both standing and seated zones via the InventorySelection abstraction, simply don't include seat numbers for standing zones.
    public void reserveInventory(int zoneId, InventorySelection selection) {
        InventoryZone zone = getZone(zoneId);
        zone.reserve(selection);
    }


    //// changed name from releaseTickets to releaseInventory and added InventorySelection parameter to support seated zones as well.
    // supports both standing and seated zones via the InventorySelection abstraction, simply don't include seat numbers for standing zones.
    public void releaseInventory(int zoneId, InventorySelection selection) {
        InventoryZone zone = getZone(zoneId);
        zone.release(selection);
    }

    // supports both standing and seated zones via the InventorySelection abstraction, simply don't include seat numbers for standing zones.
    public void confirmSale(int zoneId, InventorySelection selection) {
        InventoryZone zone = getZone(zoneId);
        zone.confirmSale(selection);
    }




     public void updateStandingZoneCapacity(int zoneId, int newCapacity) {
        InventoryZone zone = getZone(zoneId);
        zone.setStandingCapacity(newCapacity);
    }
}