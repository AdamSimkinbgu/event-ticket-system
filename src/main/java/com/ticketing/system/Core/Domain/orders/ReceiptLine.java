package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;

public class ReceiptLine {

    private final int ticketId;
    private final double price;
    private final int eventid;
    private final LocalDateTime addedAt;

      public ReceiptLine(int ticketId, double price, int eventid, LocalDateTime addedAt) {
        this.ticketId = ticketId;
        this.price = price;
        this.eventid = eventid;
        this.addedAt = addedAt;
    }

    public boolean isExpired() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isExpired'");
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getEventId() {
        return eventid;
    }

    public double getPriceAtReservation() {
        return price;
    }
    
}
