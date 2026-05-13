package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
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

    private final AuthenticationService authenticationService; // For user identity verification, if needed for future methods.
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository; // For event name lookups in history records.

    public MemberAccountService(
            AuthenticationService authenticationService,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository,
            IEventRepository eventRepository
    ) {
        this.authenticationService = authenticationService;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    // UC-16: comprehensive personal purchase history + real-time status of upcoming tickets.
    // Authorization is a domain concern: only returns history for the authenticated user.
    public PurchaseHistoryDTO viewMyHistory(AuthTokenDTO authToken) {
        try {
            // TODO: Log the request (pseudocode, depends on logging framework).
            if (!authenticationService.validateToken(authToken.token())) {
                throw new SecurityException("Invalid auth token");
            }
            int userId = authenticationService.extractUserId(authToken.token());
            System.out.println("User " + userId + " requested purchase history.");
            List<OrderReceipt> receipts = orderReceiptRepository.findByHolderUserId(userId);
            List<PurchaseRecordDTO> purchaseRecords = new ArrayList<>();

            for (OrderReceipt receipt : receipts) {
                purchaseRecords.add(mapToPurchaseRecordDTO(receipt));
            }

            // TODO: Log successful retrieval.
            return new PurchaseHistoryDTO(purchaseRecords);

        } catch (SecurityException e) {
            // TODO: Log the error with details for debugging.
            System.err.println("Error retrieving purchase history for user " + e.getMessage());
        } catch (Exception e) {
            // TODO: Log unexpected errors with stack trace for debugging.
            // Catch-all for unexpected errors; in production, would want more specific handling.
            System.err.println("Unexpected error retrieving purchase history: " + e.getMessage());
        }
        return new PurchaseHistoryDTO(new ArrayList<>()); // Return empty history on failure.
    }

    private PurchaseRecordDTO mapToPurchaseRecordDTO(OrderReceipt receipt) {
        List<PurchaseHistoryDTO.TicketRecordDTO> ticketRecords = new ArrayList<>();
        Event event = eventRepository.findById(receipt.geteventId());
        for (var ticket : ticketRepository.findByOrderReceiptId(receipt.getId())) {
            ticketRecords.add(ticket.toTicketRecordDTO());
        }
        return new PurchaseRecordDTO(
                receipt.getId(),
                receipt.geteventId(),
                event.getName(), // Would need to query event details for the name.
                receipt.getPurchaseTime(), // Assuming first transaction is purchase time.
                receipt.getTotalAmount(),
                ticketRecords
        );
    }
}
