
package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder.In;




public class Event {
    private final int id;
    private final String name;
     private final int comapnyid;
    private final VenueMap venueMap;
    private final List<ShowDate> showDates;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;

    public Event( int id, String name, int comapnyid,VenueMap venueMap,List<ShowDate> showDates, PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy
    ) {
        this.id = id;
        this.name = name;
        this.comapnyid=comapnyid;
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

public double calculatePrice(Map<Integer, Double> tickets,LocalDateTime now) {
    return discountPolicy.calculate(tickets,now);
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

    

    // ---------------------------------------------------------------------------
    // Skeleton additions — EventStatus lifecycle + missing getters.
    // ---------------------------------------------------------------------------

    public EventStatus getStatus() {
        throw new UnsupportedOperationException("not implemented (add status field)");
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

    public int getCompanyId() {
        return comapnyid;
    }

    public List<ShowDate> getShowDates() {
        return showDates;
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