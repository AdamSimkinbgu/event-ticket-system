package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderReceipt {

    private String eventId;
    private String zoneId;
    private double priceAtoneticketReservation;
     

    public OrderReceipt(String eventId,String zoneId, double priceAtReservation) {
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.priceAtoneticketReservation = priceAtReservation;
      
    }

    public String geteventId() {
        return eventId;
    }

    public String getzoneId() {
        return zoneId;
    }


}