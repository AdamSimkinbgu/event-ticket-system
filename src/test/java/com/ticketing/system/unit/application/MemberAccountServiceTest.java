package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO.PurchaseRecordDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.MemberAccountService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

class MemberAccountServiceTest {

    @Mock AuthenticationService authenticationService;
    @Mock IOrderReceiptRepository orderReceiptRepository;
    @Mock ITicketRepository ticketRepository;
    @Mock IEventRepository eventRepository;

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
        int eventId   = 10;

        OrderReceipt receipt = mockReceipt(receiptId, USER_ID, eventId, new BigDecimal("150.00"));
        Ticket ticket        = mockTicket(101, receiptId, "A", 5, new BigDecimal("75.00"), "ACTIVE");
        Event event          = mockEvent(eventId, "Rock Concert");

        stubValidToken(USER_ID);
        when(orderReceiptRepository.findByHolderUserId(USER_ID)).thenReturn(List.of(receipt));
        when(eventRepository.findById(eventId)).thenReturn(event);
        when(ticketRepository.findByOrderReceiptId(receiptId)).thenReturn(List.of(ticket));

        // Act
        PurchaseHistoryDTO result = service.viewMyHistory(validAuth);

        // Assert — one purchase record with one ticket
        assertThat(result.purchaseRecords()).hasSize(1);

        PurchaseRecordDTO record = result.purchaseRecords().get(0);
        assertThat(record.receiptId()).isEqualTo(receiptId);
        assertThat(record.eventId()).isEqualTo(eventId);
        assertThat(record.eventName()).isEqualTo("Rock Concert");
        assertThat(record.totalAmount()).isEqualByComparingTo("150.00");

        assertThat(record.tickets()).hasSize(1);
        assertThat(record.tickets().get(0).ticketId()).isEqualTo(101);
        assertThat(record.tickets().get(0).status()).isEqualTo("ACTIVE");
    }

    @Test @Disabled("UC-16: cannot view another member's history")
    void givenOtherUserId_whenViewMyHistory_thenRejected() {}

    @Test @Disabled("UC-16 + II.3.5.2: history reflects price-at-purchase, not current price")
    void givenPriceChangedAfterSale_whenViewMyHistory_thenOriginalPriceShown() {}
}
