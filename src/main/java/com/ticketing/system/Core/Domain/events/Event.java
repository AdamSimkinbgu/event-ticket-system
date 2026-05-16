
package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder.In;

public class Event {
    private final int id;
    private final String name;
    private final Double rating;
    private final List<String> artistsNames;
    private final EventCategory category;
    private final int comapnyid;
    private final EventStatus status;
    private final VenueMap venueMap;
    private final List<ShowDate> showDates;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;

    public Event( int id, String name, Double rating, List<String> artistsNames, EventCategory category, int comapnyid, EventStatus status, VenueMap venueMap, List<ShowDate> showDates, PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy
    ) {
        this.id = id;
        this.name = name;
        this.rating = rating;
        this.artistsNames = artistsNames;
        this.category = category;
        this.comapnyid=comapnyid;
        this.status = status;
        this.venueMap = venueMap;
        this.showDates = showDates;
        this.purchasePolicy = purchasePolicy;
        this.discountPolicy = discountPolicy;
    }

    public InventoryZone getZone(int zoneId) {
        for (InventoryZone zone : venueMap.getInventoryZones()) {
            if ( zoneId == (zone.getId())) {
                return zone;
            }
        }

        throw new IllegalArgumentException("Zone not found");
    }


    public boolean checkAvailability(int zoneId, int quantity) {
    
        if( venueMap.checkAvailability(zoneId, quantity)){
        return true;
        }
        return false;
    }

    public boolean reserveTickets(int zoneId, int quantity) {
      
        if (checkAvailability(zoneId, quantity)&& purchasePolicy.validate(quantity)){
        InventoryZone zone = getZone(zoneId);
            zone.reserve(quantity);
            return true;
        }
        return false;  
  
    }

    public int getId() {
        return id;
    }


    public boolean releaseTickets(int zoneId, int quantity) {
        InventoryZone zone = getZone(zoneId);
        return zone.release(quantity);
    }

    public double calculatePrice(int quantity,double priceresrevation,LocalDateTime now) {
        return discountPolicy.calculateFinalPrice(quantity,priceresrevation,now);
    }
public double calculatePriceforoneticket(int quantity,double priceresrevation,LocalDateTime now) {
        return discountPolicy.calculatePriceforoneticket(quantity,priceresrevation,now);
    }


    public void updateZoneCapacity(int zoneId, int newCapacity, int incomingCompanyId) {
        
        
        if (comapnyid != incomingCompanyId) {
            throw new RuntimeException("Unauthorized to update zone capacity");
        }
       
        if (this.venueMap == null) {
            throw new RuntimeException("Venue map must be initialized first");
        }

        InventoryZone zone = this.venueMap.getZone(zoneId);
        

        zone.setCapacity(newCapacity); 
    }

    
    



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
}