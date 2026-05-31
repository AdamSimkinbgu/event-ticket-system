package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.TicketRecordDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.MemberAccountService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {

    @Mock AuthenticationService authenticationService;
    @Mock IOrderReceiptRepository orderReceiptRepository;
    @Mock ITicketRepository ticketRepository;
    @Mock IEventRepository eventRepository;
    @Mock OrderReceipt receipt;
    @Mock Ticket ticket;
    @Mock Event event;

    MemberAccountService service;

    static final String VALID_TOKEN   = "valid-token-abc";
    static final String INVALID_TOKEN = "bad-token-xyz";
    static final int    USER_ID       = 42;

    @BeforeEach
    void setUp() {
        service = new MemberAccountService(
                authenticationService,
                orderReceiptRepository,
                ticketRepository,
                eventRepository
        );
    }

    @Test @Disabled("UC-16: viewMyHistory returns own purchase history")
    void givenAuthenticatedMember_whenViewMyHistory_thenOwnHistoryReturned() {
        // Arrange
        int receiptId = 1;
        int eventId = 10;
        LocalDateTime purchaseTime = LocalDateTime.of(2024, 1, 15, 14, 30);
        AuthTokenDTO validAuth = new AuthTokenDTO(VALID_TOKEN, 1000, USER_ID, "member42");
        List<Ticket> tickets = List.of(ticket);
        List<OrderReceipt> receipts = List.of(receipt);

        Mockito.when(authenticationService.validateToken(VALID_TOKEN)).thenReturn(true);
        Mockito.when(authenticationService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        Mockito.when(orderReceiptRepository.findByHolderUserId(USER_ID)).thenReturn(receipts);
        Mockito.when(eventRepository.findById(eventId)).thenReturn(event);
        Mockito.when(ticketRepository.findByOrderReceiptId(receiptId)).thenReturn(tickets);
        Mockito.when(receipt.getId()).thenReturn(receiptId);
        Mockito.when(receipt.geteventId()).thenReturn(eventId);
        Mockito.when(receipt.getPurchaseTime()).thenReturn(purchaseTime);
        Mockito.when(receipt.getTotalAmount()).thenReturn(150.00);
        Mockito.when(event.getName()).thenReturn("Rock Concert");
        Mockito.when(ticket.toTicketRecordDTO()).thenReturn(new TicketRecordDTO(
                101, 1, 2, 12, "15", 150.00, TicketStatus.AVAILABLE
        ));


        // Act
        PurchaseHistoryDTO result = service.viewMyHistory(validAuth);

        // Assert — one purchase record with one ticket
        assertThat(result.records()).hasSize(1);

        PurchaseRecordDTO record = result.records().getFirst();
        assertThat(record.orderReceiptId()).isEqualTo(receiptId);
        assertThat(record.eventId()).isEqualTo(eventId);
        assertThat(record.eventName()).isEqualTo("Rock Concert");
        assertThat(record.totalPaid()).isEqualByComparingTo(150.00);

        assertThat(record.tickets()).hasSize(1);
        assertThat(record.tickets().getFirst().ticketId()).isEqualTo(101);
        assertThat(record.tickets().getFirst().currentStatus()).isEqualTo(TicketStatus.AVAILABLE);
    }

    @Test @Disabled("UC-16: cannot view another member's history")
    void givenOtherUserId_whenViewMyHistory_thenRejected() {
        Mockito.when(authenticationService.validateToken(INVALID_TOKEN)).thenReturn(false);
        AuthTokenDTO badAuth = new AuthTokenDTO(INVALID_TOKEN, 0, 0, "hacker");

        PurchaseHistoryDTO result = service.viewMyHistory(badAuth);
        // service swallowed the auth failure and returned an empty history
        assertThat(result.records()).isEmpty();

        // ensure auth was checked and no data access occurred
        Mockito.verify(authenticationService).validateToken(INVALID_TOKEN);
        Mockito.verify(orderReceiptRepository, Mockito.never()).findByHolderUserId(Mockito.anyInt());
        Mockito.verify(ticketRepository, Mockito.never()).findByOrderReceiptId(Mockito.anyInt());
        Mockito.verify(eventRepository, Mockito.never()).findById(Mockito.anyInt());
    }

    @Test @Disabled("UC-16 + II.3.5.2: history reflects price-at-purchase, not current price")
    void givenPriceChangedAfterSale_whenViewMyHistory_thenOriginalPriceShown() {
    }
}
