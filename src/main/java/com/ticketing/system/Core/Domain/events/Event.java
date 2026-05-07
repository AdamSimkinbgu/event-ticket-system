
package com.ticketing.system.Core.Domain.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityNotFoundException;

import java.util.List;

public class Event {
    private final String id;
    private final String name;

    private final VenueMap venueMap;
    private final List<ShowDate> showDates;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;

    public Event(
            String id,
            String name,
            VenueMap venueMap,
            List<ShowDate> showDates,
            PurchasePolicy purchasePolicy,
            DiscountPolicy discountPolicy
    ) {
        this.id = id;
        this.name = name;
        this.venueMap = venueMap;
        this.showDates = showDates;
        this.purchasePolicy = purchasePolicy;
        this.discountPolicy = discountPolicy;
    }

    public InventoryZone getZone(String zoneId) {
    for (InventoryZone zone : venueMap.getInventoryZones()) {
        if (zoneId != null && zoneId.equals(zone.getId())) {
            return zone;
        }
    }

    throw new IllegalArgumentException("Zone not found");
}

    public boolean checkAvailability(String zoneId, int quantity) {
    
      if( !purchasePolicy.validate(quantity)){
        throw new IllegalArgumentException("purchasePolicy not validate");
      }
      if( venueMap.checkAvailability(zoneId, quantity)){
       return true;
    }
    return false;
    }
    public boolean reserveTickets(String zoneId, int quantity) {
      
     if (checkAvailability(zoneId, quantity)&& purchasePolicy.validate(quantity)){
      InventoryZone zone = getZone(zoneId);
        zone.reserve(quantity);
        return true;
     }
      return false;  
  
    }

    public String getId() {
        return id;
    }
}