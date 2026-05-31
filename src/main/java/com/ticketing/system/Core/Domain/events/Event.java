
package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

import jakarta.persistence.criteria.CriteriaBuilder.In;

public class Event implements InvariantChecked {
    private final int id;
    private final String name;
    private final Double rating;
    private final List<String> artistsNames;
    private final EventCategory category;
    private final int comapnyid;
    private final EventStatus status;
    private VenueMap venueMap;
    private final List<ShowDate> showDates;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;
    private Boolean isCanceled = false;

    public Event(int id, String name, Double rating, List<String> artistsNames, EventCategory category, int comapnyid,
            EventStatus status, VenueMap venueMap, List<ShowDate> showDates, PurchasePolicy purchasePolicy,
            DiscountPolicy discountPolicy) {
        this.id = id;
        this.name = name;
        this.rating = rating;
        this.artistsNames = artistsNames;
        this.category = category;
        this.comapnyid = comapnyid;
        this.status = status;
        this.venueMap = venueMap;
        this.showDates = showDates;
        this.purchasePolicy = purchasePolicy;
        this.discountPolicy = discountPolicy;
    }

    // for standing zones, the quantity is the only relevant parameter; for seated zones, the seat numbers are required and quantity is derived from them (must match seatNumbers.size()).

    public boolean reserveStandingSpots(int zoneId, int quantity) {
        if (!purchasePolicy.validate(quantity)) {
            throw new IllegalStateException("Purchase policy rejected this quantity");
        }
        this.venueMap.reserveInventory(zoneId, InventorySelection.standing(quantity));
        return true;
    }

    public boolean releaseStandingSpots(int zoneId, int quantity) {
        this.venueMap.releaseInventory(zoneId, InventorySelection.standing(quantity));
        return true;
    }

    public void confirmStandingSpotSale(int zoneId, int quantity) {
        this.venueMap.confirmSale(zoneId, InventorySelection.standing(quantity));
    }

    // for seated zones, the seat numbers must be provided in the InventorySelection; the zone will validate them and throw if any are invalid.

    public void reserveSeats(int zoneId, List<String> seatNumbers) {
        if (!purchasePolicy.validate(seatNumbers.size())) {
            throw new IllegalStateException("Purchase policy rejected this quantity");
        }
        this.venueMap.reserveInventory(zoneId, InventorySelection.seated(seatNumbers));
    }

    public void releaseSeats(int zoneId, List<String> seatNumbers) {
        this.venueMap.releaseInventory(zoneId, InventorySelection.seated(seatNumbers));
    }

    public void confirmSeatSale(int zoneId, List<String> seatNumbers) {
        this.venueMap.confirmSale(zoneId, InventorySelection.seated(seatNumbers));
    }

    public boolean checkAvailability(int zoneId, int quantity) {
        return this.venueMap.checkAvailability(zoneId, quantity);
    }

    public int getId() {
        return id;
    }

    public double calculatePrice(int quantity, Double priceAtoneticketReservation, LocalDateTime now) {
        return discountPolicy.calculate(quantity, priceAtoneticketReservation, now);
    }

    public double calculatePriceforoneticket(int quantity, Double priceAtoneticketReservation, LocalDateTime now) {
        return discountPolicy.calculatePriceforoneticket(quantity, priceAtoneticketReservation, now);
    }

    // UC-20 — only company that owns the event can configure the venue map; must be done before going ON_SALE. 
    // For simplicity, we allow configuring the entire map at once, rather than incremental updates.
    public void configureVenueMap(VenueMap venueMap, int incomingCompanyId) {
        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to configure venue map");
        }

        if (venueMap == null) {
            throw new IllegalArgumentException("Venue map cannot be null");
        }

        this.venueMap = venueMap;
    }

    public void updateStandingZoneCapacity(int zoneId, int newCapacity, int incomingCompanyId) {

        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to update zone capacity");
        }

        if (this.venueMap == null) {
            throw new RuntimeException("Venue map must be initialized first");
        }

        InventoryZone zone = this.venueMap.getZone(zoneId);

        if (!zone.isStanding()) {
            throw new IllegalStateException("Cannot update capacity of seated zone directly");
        }

        zone.setStandingCapacity(newCapacity);
    }



    //TODO: might need to implement in EventManagementService or in Event or we can say seated layout is immutable after venue configuration.
    // public void addSeatToSeatedZone(...){
    //TODO: might need to add a seat ID generator in VenueMap or SeatedZone to ensure unique seat IDs within the zone; or we can require the client to provide unique seat IDs in the request.
    // }

    // public void removeSeatFromSeatedZone(...){
    //TODO: might need to check if the seat is already reserved/sold before allowing removal, and handle that accordingly (e.g. prevent removal, or allow removal but mark any affected reservations as invalid and notify users, etc.)
    // }

    
    
    
    // UC-19 / UC-32 — DRAFT/SCHEDULED -> ON_SALE when admin opens or owner publishes.
    public void transitionToOnSale() {
        throw new UnsupportedOperationException("UC-19/32: not implemented");
    }

    // UC-19 — soft cancel; fires EventCancelled event for UC-4.
    public void transitionToCanceled(String reason) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }

    // ON_SALE -> COMPLETED after the last show date.
    public void transitionToCompleted() {
        throw new UnsupportedOperationException("not implemented");
    }

    // ON_SALE -> SOLD_OUT when no AVAILABLE tickets remain.
    public void markSoldOut() {
        throw new UnsupportedOperationException("not implemented");
    }

    // UC-19 — II.3.5.2 immutability check; returns false if 'field' is frozen by sales.
    public boolean canBeEdited(String field) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }

    // Missing getters.
    public String getName() {
        return name;
    }

    public Double getRating() {
        return rating;
    }

    public int getCompanyId() {
        return comapnyid;
    }

    public EventStatus getStatus() {
        return status;
    }

    public EventCategory getCategory() {
        return category;
    }

    public List<String> getArtistsNames() {
        // create a new list to prevent external modification of the internal artistsNames list
        return List.copyOf(artistsNames);
    }

    public List<ShowDate> getShowDates() {
        return List.copyOf(showDates);
    }

    public VenueMap getVenueMap() {
        return venueMap;
    }

    public PurchasePolicy getPurchasePolicy() {
        return purchasePolicy;
    }

    public DiscountPolicy getDiscountPolicy() {
        return discountPolicy;
    }

    public void setCanceled(boolean b) {
        this.isCanceled = b;
    }

    public boolean isCancelled() {
        return this.isCanceled;
    }

    @Override
    public void checkInvariants() {
        if (id <= 0) {
            throw new IllegalStateException("Event invariant violated: id must be positive (was " + id + ")");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Event invariant violated: name must be non-blank");
        }
        if (comapnyid <= 0) {
            throw new IllegalStateException(
                    "Event invariant violated: companyId must be positive (was " + comapnyid + ")");
        }
        if (status == null) {
            throw new IllegalStateException("Event invariant violated: status must not be null");
        }
        if (category == null) {
            throw new IllegalStateException("Event invariant violated: category must not be null");
        }
        if (artistsNames == null) {
            throw new IllegalStateException("Event invariant violated: artistsNames list must not be null");
        }
        if (showDates == null) {
            throw new IllegalStateException("Event invariant violated: showDates list must not be null");
        }
        if (isCanceled == null) {
            throw new IllegalStateException("Event invariant violated: isCanceled flag must not be null");
        }
        // venueMap may be null in DRAFT state (UC-19) before UC-20 binds it — don't enforce non-null.
        // If present, the VenueMap's own invariants apply (cascade-check when implemented).
    }

}