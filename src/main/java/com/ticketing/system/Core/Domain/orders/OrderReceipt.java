package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;
import java.util.List;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class OrderReceipt implements InvariantChecked {
    // Dual identity (D5 / auth rework): Member receipts carry userid; Guest
    // receipts carry guestEmail + guestSessionId. Exactly one branch is set.
    private Integer userid;
    private String guestEmail;
    private String guestSessionId;
    List<ReceiptLine> ReceiptLine;
    private int receiptId;
    private int eventId;
    private int zoneId;
    private double totalPrice;
    private LocalDateTime purchaseTime;
    private Boolean isRefunded = false;


    /**
     * Legacy Member-only constructor. {@code _userid} is autoboxed into the
     * {@code Integer} field. Equivalent to {@link #forMember(int, double, List)}.
     */
    public OrderReceipt(int _userid, double priceAtReservation, List<ReceiptLine> receiptLines) {
        this.userid = _userid;
        this.totalPrice = priceAtReservation;
        this.ReceiptLine = receiptLines;
        this.purchaseTime = LocalDateTime.now();
    }
    
    public OrderReceipt(int receiptId, int userId, double totalPrice, List<ReceiptLine> lines){
        this.receiptId = receiptId;
        this.userid = userId;
        this.totalPrice = totalPrice;
        this.ReceiptLine = lines;
        this.purchaseTime = LocalDateTime.now();
    }

    /** Member receipt — explicit construction. */
    public static OrderReceipt forMember(int receiptId, int userId, double totalAmount, List<ReceiptLine> receiptLines) {
        return new OrderReceipt(receiptId, userId, totalAmount, receiptLines);
    }

    /** Guest receipt — D5 reversed. userid stays null; email + sessionId identify the buyer. */
    public static OrderReceipt forGuest(String guestEmail, String guestSessionId, int receiptId, double totalAmount, List<ReceiptLine> receiptLines) {
        OrderReceipt r = new OrderReceipt();
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new IllegalArgumentException("forGuest requires a non-blank email");
        }
        if (guestSessionId == null || guestSessionId.isBlank()) {
            throw new IllegalArgumentException("forGuest requires a non-blank sessionId");
        }
        r.userid = null;
        r.guestEmail = guestEmail;
        r.receiptId = receiptId;
        r.guestSessionId = guestSessionId;
        r.totalPrice = totalAmount;
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
        return totalPrice;
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

    @Override
    public void checkInvariants() {
        if (totalPrice < 0) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: totalPrice must be non-negative (was " + totalPrice + ")");
        }
        if (purchaseTime == null) {
            throw new IllegalStateException("OrderReceipt invariant violated: purchaseTime must not be null");
        }
        // Dual-identity rule: exactly one of userid OR (guestEmail + guestSessionId) is set.
        boolean isMember = userid != null;
        boolean isGuest = guestEmail != null && guestSessionId != null;
        if (isMember == isGuest) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: must be exactly Member (userid set) OR Guest (email+sessionId set)");
        }
        if (isRefunded == null) {
            throw new IllegalStateException("OrderReceipt invariant violated: isRefunded must not be null");
        }
    }

}