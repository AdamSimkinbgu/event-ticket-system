package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

// Read-side service for member-facing personal account queries.
// Owns UC-16 (View Personal Purchase History).
// Reserved for additional member-side reads (UC-15 if it returns from Cancelled, profile views, etc.).
// Separated from AuthenticationService (which is auth-flow only) so personal-data reads don't
// stretch the auth boundary — see design_walkthrough_summary.md §6.
public class MemberAccountService {

    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;

    public MemberAccountService(
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository
    ) {
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
    }

    // UC-16: comprehensive personal purchase history + real-time status of upcoming tickets.
    // Authorization is a domain concern: only returns history for the authenticated user.
    public PurchaseHistoryDTO viewMyHistory(int authenticatedUserId) {
        throw new UnsupportedOperationException("UC-16: not implemented");
    }
}
