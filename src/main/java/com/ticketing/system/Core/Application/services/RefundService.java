package com.ticketing.system.Core.Application.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.exceptions.BusinessRuleViolationException;
import com.ticketing.system.Core.Domain.exceptions.EntityNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.RefundFailedException;
import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;

import lombok.extern.slf4j.Slf4j;

/**
 * Member-initiated refund of one owned order (I.3.3 / #64, #284). The owner/admin side of refunds
 * lives elsewhere ({@link EventManagementService#cancelEventAndRefund}); this is the buyer's
 * "Request refund" path from {@code ReceiptView} / {@code MyAccountView}.
 *
 * <p>Mirrors the cancellation refund mechanics, scoped to a single member-owned receipt: charge
 * the gateway refund first, then flip the receipt + its tickets to refunded. Refunds are
 * <strong>immediate</strong> (no request lifecycle exists in the domain) and whole-order (the
 * receipt's refunded flag is all-or-nothing). It deliberately dispatches <strong>no</strong>
 * notification — real-time notifications (I.5) are a Grade ג' V2 exemption, which also keeps this
 * flow clear of the unimplemented {@code NotificationDispatchService.dispatchFromEvent} (#304).
 *
 * <p>Throws typed domain exceptions; the presenter translates them into UI outcomes.
 */
@Service
@Slf4j
public class RefundService {

    private final AuthenticationService authenticationService;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketRepository ticketRepository;
    private final IPaymentGateway paymentGateway;

    public RefundService(
            AuthenticationService authenticationService,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketRepository ticketRepository,
            IPaymentGateway paymentGateway) {
        this.authenticationService = authenticationService;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketRepository = ticketRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Refunds the whole order identified by {@code orderId} on behalf of the authenticated member.
     *
     * @throws InvalidTokenException          token missing/invalid
     * @throws EntityNotFoundException        no such receipt
     * @throws UnauthorizedActionException    the receipt isn't the caller's (403)
     * @throws BusinessRuleViolationException the order isn't refund-eligible (already refunded)
     * @throws RefundFailedException          the gateway refund failed / no original charge
     */
    public RefundResultDTO requestRefund(String token, int orderId, String reason) {
        // Don't log the raw reason — it's user free-text and may carry sensitive data; length only.
        log.info("Member refund requested for order {} (reason length: {})",
                orderId, reason == null ? 0 : reason.length());
        if (!authenticationService.validateToken(token)) {
            throw new InvalidTokenException();
        }
        int userId = authenticationService.extractUserId(token);

        OrderReceipt receipt = orderReceiptRepository.findByOrderReceiptId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("OrderReceipt", orderId));

        Integer holderUserId = receipt.getHolderUserId();
        if (holderUserId == null || holderUserId != userId) {
            throw new UnauthorizedActionException("refund order " + orderId, userId);
        }

        if (receipt.wasRefunded()) {
            throw new BusinessRuleViolationException("Order " + orderId + " has already been refunded");
        }

        double refundAmount = receipt.getTotalAmount();
        // Charge the gateway BEFORE touching domain state — we never want a receipt marked refunded
        // while the gateway disagrees.
        int paymentTransactionId = receipt.getPaymentTransactionId()
                .orElseThrow(() -> new RefundFailedException(orderId, "receipt does not contain a payment transaction"));

        RefundResultDTO refundResult = paymentGateway.refund(paymentTransactionId, refundAmount);
        validateRefundResult(orderId, refundAmount, refundResult);

        receipt.markRefunded(TransactionRecord.refund(
                refundResult.refundTransactionId(),
                paymentGateway.getId(),
                refundResult.totalRefunded(),
                receipt.getPaymentCurrency(),
                refundResult.refundedAt()));
        orderReceiptRepository.save(receipt);

        // PAID/ISSUED tickets become REFUNDED; anything else (e.g. reserved-but-unissued) is voided.
        List<Ticket> tickets = ticketRepository.findByOrderReceiptId(orderId);
        for (Ticket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.ISSUED) {
                ticket.markRefunded();
            } else {
                ticket.markVoided();
            }
            ticketRepository.save(ticket);
        }

        log.info("Member refund completed for order {} — {} refunded (ref {})",
                orderId, refundResult.totalRefunded(), refundResult.refundTransactionId());
        return refundResult;
    }

    private void validateRefundResult(int receiptId, double expectedRefundAmount, RefundResultDTO refundResult) {
        if (refundResult == null) {
            throw new RefundFailedException(receiptId, "payment gateway returned null refund result");
        }
        if (refundResult.refundTransactionId() == null || refundResult.refundTransactionId().isBlank()) {
            throw new RefundFailedException(receiptId, "refund transaction id is missing");
        }
        if (Math.abs(refundResult.totalRefunded() - expectedRefundAmount) > 0.0001) {
            throw new RefundFailedException(receiptId, "refund amount mismatch");
        }
        if (refundResult.refundedAt() == null) {
            throw new RefundFailedException(receiptId, "refund timestamp is missing");
        }
    }
}
