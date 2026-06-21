package com.ticketing.system.Core.Domain.events;

import java.util.List;

import java.util.ArrayList;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class VenueMap implements InvariantChecked {
    private int id;
    private Location location;
    private final List<InventoryZone> inventoryZones;
    private int nextZoneId;

    public VenueMap(int id, Location location, List<InventoryZone> inventoryZones) {
        // Null-guarded here because the list is copied before checkInvariants() runs;
        // a null input would otherwise NPE in the copy rather than fail cleanly.
        if (inventoryZones == null) {
            throw new IllegalArgumentException("Inventory zones cannot be null");
        }

        this.id = id;
        this.location = location;
        this.inventoryZones = new ArrayList<>(inventoryZones);
        // initialize nextZoneId to one greater than the max existing zone ID, or 1 if there are no zones.
        // This is for convenience when adding new zones to the venue map.
        this.nextZoneId = this.inventoryZones.stream()
                .mapToInt(InventoryZone::getId)
                .max()
                .orElse(0) + 1;
        checkInvariants();
    }

    @Override
    public void checkInvariants() {
        if (inventoryZones == null) {
            throw new IllegalStateException("VenueMap invariant violated: inventoryZones must not be null");
        }
        if (nextZoneId < 1) {
            throw new IllegalStateException("VenueMap invariant violated: nextZoneId must be >= 1 (was " + nextZoneId + ")");
        }
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

    // True if any zone still has at least one AVAILABLE place/seat across the venue.
    public boolean hasAvailableInventory() {
        return inventoryZones.stream().anyMatch(zone -> zone.getAvailableAmount() > 0);
    }





    public void addPlacesToStandingZone(int zoneId, int amountToAdd) {
        StandingZone zone = getStandingZoneOrThrow(zoneId);
        zone.addPlaces(amountToAdd);
    }

    public void removePlacesFromStandingZone(int zoneId, int amountToRemove) {
        StandingZone zone = getStandingZoneOrThrow(zoneId);
        zone.removePlaces(amountToRemove);
    }



    public void addSeatsToSeatedZone(int zoneId, List<Seat> seatsToAdd) {
        SeatedZone zone = getSeatedZoneOrThrow(zoneId);
        zone.addSeats(seatsToAdd);
    }

    public void removeSeatsFromSeatedZone(int zoneId, List<String> seatLabelsToRemove) {
        SeatedZone zone = getSeatedZoneOrThrow(zoneId);
        zone.removeSeats(seatLabelsToRemove);
    }





    public int getNextZoneId() {
        return nextZoneId;
    }


    public void addZone(InventoryZone zone) {
        // zone id already given in calling method with the nextZoneId, but we validate here just in case to ensure no duplicate 
        // zone ids or names are added to the venue map, which could cause issues when trying to retrieve zones by id or name later on.
        validateNewZone(zone);

        inventoryZones.add(zone);

        if (zone.getId() >= nextZoneId) {
            nextZoneId = zone.getId() + 1;
        }
    }


    public InventoryZone removeZone(int zoneId) {
        InventoryZone zone = getZone(zoneId);

        if (zoneHasReservedOrSoldInventory(zone)) {
            throw new IllegalStateException("Cannot remove zone " + zoneId + " because it has reserved or sold inventory");
        }

        inventoryZones.remove(zone);
        return zone;
    }


    private void validateNewZone(InventoryZone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("Zone cannot be null");
        }

        if (zone.getId() <= 0) {
            throw new IllegalArgumentException("Zone id must be positive");
        }

        for (InventoryZone existingZone : inventoryZones) {
            if (existingZone.getId() == zone.getId()) {
                throw new IllegalArgumentException("Zone id already exists: " + zone.getId());
            }

            if (existingZone.getName().equalsIgnoreCase(zone.getName())) {
                throw new IllegalArgumentException("Zone name already exists: " + zone.getName());
            }
        }

        zone.checkInvariants();
    }


    private boolean zoneHasReservedOrSoldInventory(InventoryZone zone) {
        return zone.getReservedAmount() > 0 || zone.getSoldAmount() > 0;
    }









    
    // supports both standing and seated zones via the InventorySelection abstraction, simply don't include seat numbers for standing zones.
    public void reserveInventory(int zoneId, InventorySelection selection) {
        InventoryZone zone = getZone(zoneId);
        zone.reserve(selection);
    }


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












    // getter
    private StandingZone getStandingZoneOrThrow(int zoneId) {
        InventoryZone zone = getZone(zoneId);
        if (zone instanceof StandingZone standingZone) {
            return standingZone;
        } else {
            throw new IllegalStateException("Zone is not a standing zone");
        }
    }
    // getter
    private SeatedZone getSeatedZoneOrThrow(int zoneId) {
        InventoryZone zone = getZone(zoneId);
        if (zone instanceof SeatedZone seatedZone) {
            return seatedZone;
        } else {
            throw new IllegalArgumentException("Zone is not a seated zone");
        }
    }









}
