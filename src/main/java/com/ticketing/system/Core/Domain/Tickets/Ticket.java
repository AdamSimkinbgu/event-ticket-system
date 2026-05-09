package com.ticketing.system.Core.Domain.Tickets;




public class Ticket {
  
    private String zoneid;
    private String eventId;
    private double price;


    public Ticket(String eventId,String zoneid ,double price) {
        this.eventId = eventId;
         this.zoneid=zoneid;
        this.price = price;
    }

    

    public String getEventId() {
        return eventId;
    }

    public double getPrice() {
        return price;
    }   
}