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

    // ---------------------------------------------------------------------------
    // Skeleton additions for OrderReceipt aggregate.
    // ---------------------------------------------------------------------------

    public String getId() {
        throw new UnsupportedOperationException("not implemented (add id field)");
    }

    public int getHolderUserId() {
        throw new UnsupportedOperationException("not implemented (add holderUserId field)");
    }

    public double getTotalAmount() {
        throw new UnsupportedOperationException("not implemented");
    }

    public java.util.List<TransactionRecord> getTransactionRecords() {
        throw new UnsupportedOperationException("not implemented (add transactionRecords list)");
    }

    // UC-4 — append a refund TransactionRecord (after multiplicity fix to 1..*).
    public void addTransaction(TransactionRecord record) {
        throw new UnsupportedOperationException("UC-4: not implemented");
    }

    public java.util.List<ReceiptLine> getReceiptLines() {
        throw new UnsupportedOperationException("not implemented (add receiptLines list)");
    }

    public boolean wasRefunded() {
        throw new UnsupportedOperationException("not implemented");
    }
}