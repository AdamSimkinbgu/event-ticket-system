package com.ticketing.system.Core.Domain.Tickets;


import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TicketRecordDTO;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class Ticket implements InvariantChecked {

    private int zoneid;
    private int eventId;
    private String seatNumber;
    private double price;
    private int ticketId;
    private String barcodeValue;
    private TicketStatus status;
    // D7 (auth rework #181): nullable. Set on the Member path of CheckoutService;
    // stays null for Guest purchases. Lets UC-16 view-my-history go through
    // ITicketRepository.findByHolderUserId(int) in a single hop.
    private Integer holderUserId;

    //TODO: change the seat number so it will get a proper seat
    public Ticket(int eventId,int zoneid ,double price,int ticketId,String barcodeValue) {
        this.eventId = eventId;
        this.zoneid=zoneid;
        this.seatNumber="seatNumber";
        this.price = price;
        this.ticketId=ticketId;
        this.barcodeValue=barcodeValue;
        this.status = TicketStatus.PAID; // Default initial status
        this.holderUserId = null;
    }



    public int getId() {
        return ticketId;
    }

    public int getEventId() {
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
        this.barcodeValue = barcodeValue;
        this.status = TicketStatus.ISSUED;
    }

    // ISSUED -> USED at venue gate scan (no UC in v0; defined for completeness).
    public void markUsed() {
        throw new UnsupportedOperationException("not implemented");
    }

    // UC-4 — PAID/ISSUED -> REFUNDED via auto-refund pipeline.
    public void markRefunded() {
        this.status = TicketStatus.REFUNDED;
    }

    // Admin / ops action — any state -> VOIDED.
    public void markVoided() {
        this.status = TicketStatus.VOIDED;
    }

    // UC-2 — RESERVED -> AVAILABLE on cart expiration. Releases the lock.
    public void release() {
        this.status = TicketStatus.AVAILABLE;
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
    public int getZoneId() {
        return zoneid;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public Integer getHolderUserId() {
        return holderUserId;
    }

    /**
     * Assigns this ticket to a Member at checkout (D7). Called once from the
     * Member path of {@code CheckoutService.checkout}. Guest tickets stay
     * unassigned (holderUserId remains null).
     */
    public void setHolderUserId(Integer userId) {
        this.holderUserId = userId;
    }

    public String getOrderReceiptId() {
        throw new UnsupportedOperationException("not implemented (add orderReceiptId field)");
    }

    public String getBarcode() {
        throw new UnsupportedOperationException("not implemented (add barcode field)");
    }

    public TicketStatus getStatus() {
        return status;
    }



    public void setTicketId(int ticketId) {
       
       this.ticketId = ticketId;
         
    }



    public void setBarcodeValue(String barcodeValue) {
       this.barcodeValue=barcodeValue;
    }

    public TicketRecordDTO toTicketRecordDTO(){
        TicketRecordDTO dto = new TicketRecordDTO(
            this.getId(),
            this.getZoneId(),
            this.getSeatNumber(),
            this.getPrice(),
            this.getStatus()
        );
        return dto;
    }

    @Override
    public void checkInvariants() {
        if (ticketId <= 0) {
            throw new IllegalStateException("Ticket invariant violated: ticketId must be positive (was " + ticketId + ")");
        }
        if (eventId <= 0) {
            throw new IllegalStateException("Ticket invariant violated: eventId must be positive (was " + eventId + ")");
        }
        if (zoneid < 0) {
            throw new IllegalStateException("Ticket invariant violated: zoneId must be >= 0 (was " + zoneid + ")");
        }
        if (price < 0) {
            throw new IllegalStateException("Ticket invariant violated: price must be >= 0 (was " + price + ")");
        }
        if (status == null) {
            throw new IllegalStateException("Ticket invariant violated: status must not be null");
        }
        if (holderUserId != null && holderUserId <= 0) {
            throw new IllegalStateException("Ticket invariant violated: holderUserId must be positive when set (was " + holderUserId + ")");
        }
    }
}