package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class OrderReceipt implements InvariantChecked {

    private final int receiptId;

    // Member receipt identity
    private final Integer userid;          // nullable for guest receipts, positive integer for member receipts

    // Guest receipt identity
    private final String guestEmail;       // nullable for member receipts, non-null/non-blank for guest receipts 
    private final String guestSessionId;   // nullable for member receipts, non-null/non-blank for guest receipts

    private final List<ReceiptLine> receiptLines;
    private final double totalPrice;
    private final LocalDateTime purchaseTime;

    private Boolean isRefunded = false;

    private OrderReceipt(int receiptId, Integer userid, String guestEmail, String guestSessionId, double totalPrice, List<ReceiptLine> receiptLines) {
        if (receiptId <= 0) {
            throw new IllegalArgumentException("receiptId must be positive");
        }

        if (totalPrice < 0) {
            throw new IllegalArgumentException("totalPrice must be non-negative");
        }

        if (receiptLines == null) {
            throw new IllegalArgumentException("receiptLines must not be null");
        }

        boolean memberIdentity = userid != null;
        boolean guestIdentity = guestEmail != null && !guestEmail.isBlank() && guestSessionId != null && !guestSessionId.isBlank();

        if (memberIdentity == guestIdentity) {
            throw new IllegalArgumentException("OrderReceipt must belong to exactly one buyer type: member OR guest");
        }

        if (memberIdentity && userid <= 0) {
            throw new IllegalArgumentException("userid must be positive");
        }

        this.receiptId = receiptId;
        this.userid = userid;
        this.guestEmail = guestEmail;
        this.guestSessionId = guestSessionId;
        this.totalPrice = totalPrice;
        this.receiptLines = List.copyOf(receiptLines);
        this.purchaseTime = LocalDateTime.now();

        checkInvariants();
    }

    public static OrderReceipt forMember(int receiptId, int userId, double totalAmount, List<ReceiptLine> receiptLines) {
        return new OrderReceipt(
                receiptId,
                userId,
                null,
                null,
                totalAmount,
                receiptLines
        );
    }

    public static OrderReceipt forGuest(String guestEmail, String guestSessionId, int receiptId, double totalAmount, List<ReceiptLine> receiptLines) {
        return new OrderReceipt(
                receiptId,
                null,
                guestEmail,
                guestSessionId,
                totalAmount,
                receiptLines
        );
    }

    public int getId() {
        return receiptId;
    }

    // Keep this name because existing tests/code use it.
    public Integer getUserid() {
        return userid;
    }

    public Integer getHolderUserId() {
        return userid;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public String getGuestSessionId() {
        return guestSessionId;
    }

    public boolean isMemberReceipt() {
        return userid != null;
    }

    public boolean isGuestReceipt() {
        return guestEmail != null && guestSessionId != null;
    }

    public List<Integer> getEventIds() {
        return receiptLines.stream()
                .map(ReceiptLine::getEventId)
                .distinct()
                .toList();
    }

    public boolean containsEventId(int eventId) {
        return receiptLines.stream()
                .anyMatch(line -> line.getEventId() == eventId);
    }

    public LocalDateTime getPurchaseTime() {
        return purchaseTime;
    }

    public double getTotalAmount() {
        return totalPrice;
    }

    public List<ReceiptLine> getReceiptLines() {
        return receiptLines;
    }

    public boolean wasRefunded() {
        return isRefunded;
    }

    public void markRefunded() {
        this.isRefunded = true;
    }

    public List<TransactionRecord> getTransactionRecords() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public void addTransaction(TransactionRecord record) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void checkInvariants() {
        if (receiptId <= 0) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: receiptId must be positive"
            );
        }

        if (totalPrice < 0) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: totalPrice must be non-negative"
            );
        }

        if (purchaseTime == null) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: purchaseTime must not be null"
            );
        }

        if (receiptLines == null) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: receiptLines must not be null"
            );
        }

        boolean isMember = userid != null;
        boolean isGuest = guestEmail != null && !guestEmail.isBlank()
                && guestSessionId != null && !guestSessionId.isBlank();

        if (isMember == isGuest) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: must be exactly member OR guest"
            );
        }

        if (isMember && userid <= 0) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: userid must be positive"
            );
        }

        if (isRefunded == null) {
            throw new IllegalStateException(
                    "OrderReceipt invariant violated: isRefunded must not be null"
            );
        }

        for (ReceiptLine line : receiptLines) {
            Objects.requireNonNull(line, "OrderReceipt invariant violated: receipt line must not be null");
            line.checkInvariants();
        }
    }
}