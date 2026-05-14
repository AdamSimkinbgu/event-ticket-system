package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import org.hibernate.query.spi.Limit;

public class OrderReceipt {
    private int userid;
    List<ReceiptLine> ReceiptLine;
    private String receiptId;
    private int eventId;
    private String zoneId;
    private double priceAtoneticketReservation;
    private LocalDateTime purchaseTime;
     

    public OrderReceipt(int _userid, double priceAtReservation, List<ReceiptLine> receiptLines) {
        this.userid= _userid;
        this.priceAtoneticketReservation = priceAtReservation;
        this.ReceiptLine=receiptLines;
        this.purchaseTime = LocalDateTime.now();
    }

    public int geteventId() {
        return eventId;
    }

    public String getZoneId() {
        return zoneId;
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions for OrderReceipt aggregate.
    // ---------------------------------------------------------------------------

    public String getId() {
        return receiptId;
    }

    public LocalDateTime getPurchaseTime() {
        return purchaseTime;
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