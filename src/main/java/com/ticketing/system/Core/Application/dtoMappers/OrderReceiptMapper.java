package com.ticketing.system.Core.Application.dtoMappers;

import java.util.List;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

public class OrderReceiptMapper {

    /**
     * Maps a full OrderReceipt to a PurchaseRecordDTO.
     *
     * The OrderReceipt is the purchase aggregate.
     * The Ticket objects are stored separately, but each Ticket points back to this receipt
     * through ticket.orderReceiptId.
     */
    public PurchaseHistoryDTO.PurchaseRecordDTO OrderReceiptToPurchaseRecordDTO(OrderReceipt receipt,
                    ITicketRepository ticketRepository) {

            List<Ticket> tickets = ticketRepository.findByOrderReceiptId(receipt.getId());

            return OrderReceiptToPurchaseRecordDTO(receipt, tickets);
    }

    
        //?The first method maps the full receipt.
        //? The second method maps the same receipt but with a chosen ticket list. This is useful because the mapper should not decide business filtering.


    /**
     * Maps an OrderReceipt using a specific list of tickets.
     *
     * This is useful when a service already chose which tickets are relevant.
     * For example, CompanyManagementService may pass only the tickets that belong
     * to that company.
     *
     * Important: this method still does not know anything about companies.
     * It only maps the receipt + the tickets it was given. (encapsulation of the mapping logic, not of the business filtering logic)
     */
    public PurchaseHistoryDTO.PurchaseRecordDTO OrderReceiptToPurchaseRecordDTO(OrderReceipt receipt, List<Ticket> tickets) {

            double totalAmount = tickets.stream()
                            .mapToDouble(Ticket::getPrice)
                            .sum();

            List<PurchaseHistoryDTO.TicketRecordDTO> ticketRecords = tickets.stream()
                            .map(Ticket::toTicketRecordDTO)
                            .toList();

            return new PurchaseHistoryDTO.PurchaseRecordDTO(
                            receipt.getId(),
                            receipt.getPurchaseTime(),
                            totalAmount,
                            ticketRecords);
    }

    
}