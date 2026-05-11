package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;


import java.util.ArrayList;
import java.util.List;

// Read-side service for member-facing personal account queries.
// Owns UC-16 (View Personal Purchase History).
// Reserved for additional member-side reads (UC-15 if it returns from Cancelled, profile views, etc.).
// Separated from AuthenticationService (which is auth-flow only) so personal-data reads don't
// stretch the auth boundary — see design_walkthrough_summary.md §6.
public class MemberAccountService {

    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository; // For event name lookups in history records.

    public MemberAccountService(
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository,
            IEventRepository eventRepository
    ) {
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    // UC-16: comprehensive personal purchase history + real-time status of upcoming tickets.
    // Authorization is a domain concern: only returns history for the authenticated user.
    public PurchaseHistoryDTO viewMyHistory(int authenticatedUserId) {

        // Implementation would:
        // 1. Log the request for audit purposes.
        // 2. Query order receipts for the user, sorted by most recent.
        // 3. For each receipt, gather ticket details and current status (e.g., valid, cancelled, used).
        // 4. Compile this into a PurchaseHistoryDTO and return it.

        try {
            // Log the request (pseudocode, depends on logging framework).
            System.out.println("User " + authenticatedUserId + " requested purchase history.");
            List<OrderReceipt> receipts = orderReceiptRepository.findByHolderUserId(authenticatedUserId);
            List<PurchaseRecordDTO> purchaseRecords = new ArrayList<>();

            for (OrderReceipt receipt : receipts) {
                List<PurchaseHistoryDTO.TicketRecordDTO> ticketRecords = new ArrayList<>();
                Event event = eventRepository.findById(receipt.geteventId());
                for (var ticket : ticketRepository.findByOrderReceiptId(receipt.getId())) {
                    ticketRecords.add(new PurchaseHistoryDTO.TicketRecordDTO(
                            ticket.getId(),
                            ticket.getZoneId(),
                            ticket.getSeatNumber(),
                            ticket.getPrice(),
                            ticket.getStatus()
                    ));
                }
                purchaseRecords.add(new PurchaseRecordDTO(
                        receipt.getId(),
                        receipt.geteventId(),
                        event.getName(), // Would need to query event details for the name.
                        receipt.getPurchaseTime(), // Assuming first transaction is purchase time.
                        receipt.getTotalAmount(),
                        ticketRecords
                ));
            }


            return new PurchaseHistoryDTO(purchaseRecords);

        } catch (Exception e) {
            // Handle logging failure gracefully (don't fail the whole request).
            System.err.println("Logging failed for user " + authenticatedUserId + ": " + e.getMessage());
        }
        return new PurchaseHistoryDTO(new ArrayList<>()); // Return empty history on failure.
    }

}
