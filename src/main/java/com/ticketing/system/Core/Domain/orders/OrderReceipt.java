package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import org.hibernate.query.spi.Limit;

public class OrderReceipt {
    // Dual identity (D5 / auth rework): Member receipts carry userid; Guest
    // receipts carry guestEmail + guestSessionId. Exactly one branch is set.
    private Integer userid;
    private String guestEmail;
    private String guestSessionId;
    List<ReceiptLine> ReceiptLine;
    private int receiptId;
    private int eventId;
    private int zoneId;
    private double priceAtoneticketReservation;
    private LocalDateTime purchaseTime;
    private Boolean isRefunded = false;


    /**
     * Legacy Member-only constructor. {@code _userid} is autoboxed into the
     * {@code Integer} field. Equivalent to {@link #forMember(int, double, List)}.
     */
    public OrderReceipt(int _userid, double priceAtReservation, List<ReceiptLine> receiptLines) {
        this.userid= _userid;
        this.priceAtoneticketReservation = priceAtReservation;
        this.ReceiptLine=receiptLines;
        this.purchaseTime = LocalDateTime.now();
    }

    /** Member receipt — explicit construction. */
    public static OrderReceipt forMember(int userId, double totalAmount, List<ReceiptLine> receiptLines) {
        return new OrderReceipt(userId, totalAmount, receiptLines);
    }

    /** Guest receipt — D5 reversed. userid stays null; email + sessionId identify the buyer. */
    public static OrderReceipt forGuest(String guestEmail, String guestSessionId, double totalAmount, List<ReceiptLine> receiptLines) {
        OrderReceipt r = new OrderReceipt();
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new IllegalArgumentException("forGuest requires a non-blank email");
        }
        if (guestSessionId == null || guestSessionId.isBlank()) {
            throw new IllegalArgumentException("forGuest requires a non-blank sessionId");
        }
        r.userid = null;
        r.guestEmail = guestEmail;
        r.guestSessionId = guestSessionId;
        r.priceAtoneticketReservation = totalAmount;
        r.ReceiptLine = receiptLines;
        r.purchaseTime = LocalDateTime.now();
        return r;
    }

    /** Private no-arg constructor for the {@link #forGuest} factory. */
    private OrderReceipt() { }

    public Integer getUserid() { return userid; }
    public String getGuestEmail() { return guestEmail; }
    public String getGuestSessionId() { return guestSessionId; }
    public boolean isMemberReceipt() { return userid != null; }
    public boolean isGuestReceipt() { return userid == null; }

    public int geteventId() {
        return eventId;
    }

    public int getZoneId() {
        return zoneId;
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions for OrderReceipt aggregate.
    // ---------------------------------------------------------------------------

    public int getId() {
        return receiptId;
    }

    public LocalDateTime getPurchaseTime() {
        return purchaseTime;
    }

    public int getHolderUserId() {
        return userid;
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

    public List<ReceiptLine> getReceiptLines() {
        return this.ReceiptLine;

    }

    public boolean wasRefunded() {
        return isRefunded;
    }

    public void markRefunded() {
        this.isRefunded = true;
    }

}