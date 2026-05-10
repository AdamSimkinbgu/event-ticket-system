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

    // ---------------------------------------------------------------------------
    // Skeleton additions for the unified-Ticket aggregate.
    // State machine: AVAILABLE -> RESERVED -> PAID -> ISSUED -> USED | REFUNDED | VOIDED
    // ---------------------------------------------------------------------------

    // UC-9 / UC-5 — AVAILABLE -> RESERVED. Throws TicketNotAvailableException if not AVAILABLE.
    public void reserve(int holderUserId) {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }

    // UC-10 — RESERVED -> PAID after successful charge.
    public void markPaid() {
        throw new UnsupportedOperationException("UC-10: not implemented");
    }

    // UC-10 / UC-34 — PAID -> ISSUED after successful external issuance, stores barcode locally.
    public void markIssued(String barcodeValue) {
        throw new UnsupportedOperationException("UC-10 / UC-34: not implemented");
    }

    // ISSUED -> USED at venue gate scan (no UC in v0; defined for completeness).
    public void markUsed() {
        throw new UnsupportedOperationException("not implemented");
    }

    // UC-4 — PAID/ISSUED -> REFUNDED via auto-refund pipeline.
    public void markRefunded() {
        throw new UnsupportedOperationException("UC-4: not implemented");
    }

    // Admin / ops action — any state -> VOIDED.
    public void markVoided() {
        throw new UnsupportedOperationException("not implemented");
    }

    // UC-2 — RESERVED -> AVAILABLE on cart expiration. Releases the lock.
    public void release() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }

    // State checks.
    public boolean isAvailable() {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isReserved() {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isPaid() {
        throw new UnsupportedOperationException("not implemented");
    }

    public boolean isIssued() {
        throw new UnsupportedOperationException("not implemented");
    }

    // Missing getters per the unified-Ticket model.
    public String getZoneId() {
        return zoneid;
    }

    public String getSeatNumber() {
        throw new UnsupportedOperationException("not implemented (add seatNumber field)");
    }

    public Integer getHolderUserId() {
        throw new UnsupportedOperationException("not implemented (add holderUserId field)");
    }

    public String getOrderReceiptId() {
        throw new UnsupportedOperationException("not implemented (add orderReceiptId field)");
    }

    public String getBarcode() {
        throw new UnsupportedOperationException("not implemented (add barcode field)");
    }

    public TicketStatus getStatus() {
        throw new UnsupportedOperationException("not implemented (add status field)");
    }
}