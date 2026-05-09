
package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;




public class Event {
    private final String id;
    private final String name;
     private final String comapnyid;
    private final VenueMap venueMap;
    private final List<ShowDate> showDates;
    private final PurchasePolicy purchasePolicy;
    private final DiscountPolicy discountPolicy;

    public Event( String id, String name, String comapnyid,VenueMap venueMap,List<ShowDate> showDates, PurchasePolicy purchasePolicy, DiscountPolicy discountPolicy
    ) {
        this.id = id;
        this.name = name;
        this.comapnyid=comapnyid;
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


public boolean releaseTickets(String zoneId, int quantity) {
    InventoryZone zone = getZone(zoneId);
    return zone.release(quantity);
}

public double calculatePrice(Map<Integer, Double> tickets,LocalDateTime now) {
    return discountPolicy.calculate(tickets,now);
}

}