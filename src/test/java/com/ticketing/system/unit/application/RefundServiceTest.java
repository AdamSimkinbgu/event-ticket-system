package com.ticketing.system.unit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.RefundService;
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
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock AuthenticationService authenticationService;
    @Mock IOrderReceiptRepository orderReceiptRepository;
    @Mock ITicketRepository ticketRepository;
    @Mock IPaymentGateway paymentGateway;
    @Mock Ticket ticket;

    RefundService service;

    static final String VALID_TOKEN   = "valid-token";
    static final String INVALID_TOKEN = "bad-token";
    static final int    USER_ID       = 42;
    static final int    OTHER_USER    = 99;
    static final int    ORDER_ID      = 7;
    static final int    PAYMENT_TX    = 555;
    static final double TOTAL         = 150.00;
    static final LocalDateTime WHEN   = LocalDateTime.of(2026, 6, 1, 12, 0);

    @BeforeEach
    void setUp() {
        service = new RefundService(authenticationService, orderReceiptRepository, ticketRepository, paymentGateway);
    }

    private static OrderReceipt memberReceiptWithCharge(int userId) {
        ReceiptLine line = new ReceiptLine(101, TOTAL, 2, 1, "15", WHEN);
        TransactionRecord charge = TransactionRecord.paymentCharge(PAYMENT_TX, "stub", TOTAL, "ILS", WHEN);
        return OrderReceipt.forMember(ORDER_ID, userId, TOTAL, List.of(line), List.of(charge));
    }

    private static RefundResultDTO gatewayResult() {
        return new RefundResultDTO("refund-1", String.valueOf(PAYMENT_TX), TOTAL, WHEN, List.of(), List.of());
    }

    @Test
    void givenOwnPaidOrder_whenRequestRefund_thenReceiptAndTicketsRefunded() {
        OrderReceipt receipt = memberReceiptWithCharge(USER_ID);
        RefundResultDTO result = gatewayResult();
        Mockito.when(authenticationService.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(authenticationService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        Mockito.when(orderReceiptRepository.findByOrderReceiptId(ORDER_ID)).thenReturn(java.util.Optional.of(receipt));
        Mockito.when(paymentGateway.refund(PAYMENT_TX, TOTAL)).thenReturn(result);
        Mockito.when(paymentGateway.getId()).thenReturn("stub");
        Mockito.when(ticketRepository.findByOrderReceiptId(ORDER_ID)).thenReturn(List.of(ticket));
        Mockito.when(ticket.getStatus()).thenReturn(TicketStatus.PAID);

        RefundResultDTO returned = service.requestRefund(VALID_TOKEN, ORDER_ID, "Can't attend");

        assertThat(returned).isSameAs(result);
        assertThat(receipt.wasRefunded()).isTrue();
        Mockito.verify(paymentGateway).refund(PAYMENT_TX, TOTAL);
        Mockito.verify(ticket).markRefunded();
        Mockito.verify(ticketRepository).save(ticket);
        Mockito.verify(orderReceiptRepository).save(receipt);
    }

    @Test
    void givenInvalidToken_whenRequestRefund_thenInvalidTokenThrown() {
        Mockito.when(authenticationService.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.requestRefund(INVALID_TOKEN, ORDER_ID, "x"))
                .isInstanceOf(InvalidTokenException.class);

        Mockito.verify(paymentGateway, Mockito.never()).refund(Mockito.anyInt(), Mockito.anyDouble());
    }

    @Test
    void givenMissingOrder_whenRequestRefund_thenEntityNotFoundThrown() {
        Mockito.when(authenticationService.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(authenticationService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        Mockito.when(orderReceiptRepository.findByOrderReceiptId(ORDER_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.requestRefund(VALID_TOKEN, ORDER_ID, "x"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void givenAnotherMembersOrder_whenRequestRefund_thenUnauthorizedThrown() {
        OrderReceipt receipt = memberReceiptWithCharge(OTHER_USER);
        Mockito.when(authenticationService.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(authenticationService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        Mockito.when(orderReceiptRepository.findByOrderReceiptId(ORDER_ID)).thenReturn(java.util.Optional.of(receipt));

        assertThatThrownBy(() -> service.requestRefund(VALID_TOKEN, ORDER_ID, "x"))
                .isInstanceOf(UnauthorizedActionException.class);

        Mockito.verify(paymentGateway, Mockito.never()).refund(Mockito.anyInt(), Mockito.anyDouble());
    }

    @Test
    void givenAlreadyRefundedOrder_whenRequestRefund_thenNotEligibleThrown() {
        OrderReceipt receipt = memberReceiptWithCharge(USER_ID);
        receipt.markRefunded();
        Mockito.when(authenticationService.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(authenticationService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        Mockito.when(orderReceiptRepository.findByOrderReceiptId(ORDER_ID)).thenReturn(java.util.Optional.of(receipt));

        assertThatThrownBy(() -> service.requestRefund(VALID_TOKEN, ORDER_ID, "x"))
                .isInstanceOf(BusinessRuleViolationException.class);

        Mockito.verify(paymentGateway, Mockito.never()).refund(Mockito.anyInt(), Mockito.anyDouble());
    }

    @Test
    void givenGatewayFailure_whenRequestRefund_thenRefundFailedPropagatesAndNoStateChange() {
        OrderReceipt receipt = memberReceiptWithCharge(USER_ID);
        Mockito.when(authenticationService.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(authenticationService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        Mockito.when(orderReceiptRepository.findByOrderReceiptId(ORDER_ID)).thenReturn(java.util.Optional.of(receipt));
        Mockito.when(paymentGateway.refund(PAYMENT_TX, TOTAL))
                .thenThrow(new RefundFailedException(ORDER_ID, "gateway down"));

        assertThatThrownBy(() -> service.requestRefund(VALID_TOKEN, ORDER_ID, "x"))
                .isInstanceOf(RefundFailedException.class);

        assertThat(receipt.wasRefunded()).isFalse();
        Mockito.verify(orderReceiptRepository, Mockito.never()).save(Mockito.any());
    }
}
