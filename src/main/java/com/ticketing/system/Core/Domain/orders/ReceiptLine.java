package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class ReceiptLine implements InvariantChecked {

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

    @Override
    public void checkInvariants() {
        if (ticketId <= 0) {
            throw new IllegalStateException("ReceiptLine invariant violated: ticketId must be positive (was " + ticketId + ")");
        }
        if (eventid <= 0) {
            throw new IllegalStateException("ReceiptLine invariant violated: eventid must be positive (was " + eventid + ")");
        }
        if (price < 0) {
            throw new IllegalStateException("ReceiptLine invariant violated: price must be >= 0 (was " + price + ")");
        }
        if (addedAt == null) {
            throw new IllegalStateException("ReceiptLine invariant violated: addedAt must not be null");
        }
    }
}
