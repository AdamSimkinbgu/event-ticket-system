package com.ticketing.system.Core.Application.services;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.dtoMappers.OrderReceiptMapper;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.exceptions.EntityNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service for member-facing personal account queries. Owns UC-16
 * (View Personal Purchase History).
 *
 * <p>Reserved for additional member-side reads (UC-15 if it returns from
 * Cancelled, profile views, etc.). Separated from {@link AuthenticationService}
 * (which is auth-flow only) so personal-data reads don't stretch the auth
 * boundary — see {@code design_walkthrough_summary.md} §6.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class MemberAccountService {

    private final AuthenticationService authenticationService; // For user identity verification, if needed for future
                                                               // methods.
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;
    private final IEventRepository eventRepository; // For event name lookups in history records.

    public MemberAccountService(
            AuthenticationService authenticationService,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository,
            IEventRepository eventRepository) {
        this.authenticationService = authenticationService;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * UC-16 — returns the authenticated member's comprehensive personal purchase
     * history with the real-time status of upcoming tickets.
     *
     * <p>Authorization is a domain concern: only history for the user identified
     * by the token is returned. This method is lenient — any auth or lookup
     * failure is logged and an empty history is returned rather than thrown, so
     * the account page always renders. Use {@link #viewMyReceipt(String, int)}
     * when the caller needs to distinguish the failure modes.
     *
     * @param authToken the authenticated member's token
     * @return the member's purchase history, or an empty history on any failure
     */
    public PurchaseHistoryDTO viewMyHistory(AuthTokenDTO authToken) {
        try {
            log.debug("Received request to view purchase history.");
            if (!authenticationService.validateToken(authToken.token())) {
                throw new SecurityException("Invalid auth token");
            }
            int userId = authenticationService.extractUserId(authToken.token());
            log.debug("User {} requested purchase history.", userId);
            List<OrderReceipt> receipts = orderReceiptRepository.findByHolderUserId(userId);
            List<PurchaseRecordDTO> purchaseRecords = new ArrayList<>();

            OrderReceiptMapper receiptMapper = new OrderReceiptMapper();
            for (OrderReceipt receipt : receipts) {
                // Resolve event + zone names via eventRepository; buyer is the member
                // themselves and company is not shown on the account view, so pass null.
                purchaseRecords.add(receiptMapper.toPurchaseRecordDTO(
                        receipt, ticketRepository, eventRepository, null, null));
            }

            log.info("Successfully retrieved purchase history for userId={}, recordsCount={}", userId,
                    purchaseRecords.size());
            return new PurchaseHistoryDTO(purchaseRecords);

        } catch (SecurityException e) {
            log.error("Error retrieving purchase history for user {}: {}",
                    authenticationService.extractUserId(authToken.token()), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error retrieving purchase history for user {}: {}",
                    authenticationService.extractUserId(authToken.token()), e.getMessage(), e);
        }
        return new PurchaseHistoryDTO(new ArrayList<>()); // Return empty history on failure.
    }

    /**
     * UC-16 (single receipt, #276) — returns one member-owned order enriched for
     * the receipt page.
     *
     * <p>Unlike {@link #viewMyHistory(AuthTokenDTO)} (which degrades to an empty
     * list), this throws so the presenter can tell apart auth failure, a missing
     * receipt, and a receipt that isn't the caller's (403). Authorization is a
     * domain concern: a member may only view their own receipt.
     *
     * @param token     the authenticated member's token
     * @param receiptId the id of the receipt to view
     * @return the receipt as an enriched purchase record
     * @throws InvalidTokenException       if the token is invalid
     * @throws EntityNotFoundException     if no receipt with that id exists
     * @throws UnauthorizedActionException if the receipt belongs to another user
     */
    public PurchaseRecordDTO viewMyReceipt(String token, int receiptId) {
        log.info("Received request to view receipt {} for the authenticated member", receiptId);
        if (!authenticationService.validateToken(token)) {
            throw new InvalidTokenException();
        }
        int userId = authenticationService.extractUserId(token);

        OrderReceipt receipt = orderReceiptRepository.findByOrderReceiptId(receiptId)
                .orElseThrow(() -> new EntityNotFoundException("OrderReceipt", receiptId));

        // Authorization is a domain concern: a member may only view their own receipt.
        Integer holderUserId = receipt.getHolderUserId();
        if (holderUserId == null || holderUserId != userId) {
            throw new UnauthorizedActionException("view receipt " + receiptId, userId);
        }

        // Buyer is the member themselves and company isn't shown on the receipt, so
        // pass null for
        // those repos (the mapper yields null for the unresolved names — see
        // OrderReceiptMapper).
        return new OrderReceiptMapper().toPurchaseRecordDTO(
                receipt, ticketRepository, eventRepository, null, null);
    }

}
