package com.ticketing.system.Core.Domain.events;

import java.util.List;
import java.util.Map;

public class VenueMap {
    private int id;
    private List<InventoryZone> inventoryZones;

    public VenueMap(int id, List<InventoryZone> inventoryZones) {
        this.id = id;
        this.inventoryZones = inventoryZones;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<InventoryZone> getInventoryZones() {
        return inventoryZones;
    }

    public void setInventoryZones(List<InventoryZone> inventoryZones) {
        this.inventoryZones = inventoryZones;
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
        return zone.CheckAvailability(quantity);
    }


    public void releaseTicketsToInventory(Map<Integer, Integer> ticketsByZone) {
        for (Map.Entry<Integer, Integer> entry : ticketsByZone.entrySet()) {
            int zoneId = entry.getKey();
            int quantity = entry.getValue();

            InventoryZone zone = getZone(zoneId);
            zone.release(quantity);
        }
    }

    public void updateZone(InventoryZone zone) {
        if (!this.inventoryZones.contains(zone)) {
            throw new IllegalArgumentException("Zone not found in venue map");
        }
        for (int i = 0; i < inventoryZones.size(); i++) {
            if (inventoryZones.get(i).getId()==(zone.getId())) {
                inventoryZones.set(i, zone);
                return;
            }
        }
    }

}