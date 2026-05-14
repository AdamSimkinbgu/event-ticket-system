package com.ticketing.system.Core.Domain.Tickets;


import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TicketRecordDTO;

public class Ticket {

    private int zoneid;
    private int eventId;
    private String seatNumber;
    private double price;
    private int ticketId;
    private String barcodeValue;
    private TicketStatus status;

    //TODO: change the seat number so it will get a proper seat
    public Ticket(int eventId,int zoneid ,double price,int ticketId,String barcodeValue) {
        this.eventId = eventId;
        this.zoneid=zoneid;
        this.seatNumber="seatNumber";
        this.price = price;
        this.ticketId=ticketId;
        this.barcodeValue=barcodeValue; 
        this.status = TicketStatus.AVAILABLE; // Default initial status
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
    public int getZoneId() {
        return zoneid;
    }

    public String getSeatNumber() {
        return seatNumber;
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
}