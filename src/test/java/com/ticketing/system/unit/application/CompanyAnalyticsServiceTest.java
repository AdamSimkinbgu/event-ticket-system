package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.CompanyDashboardDTO;
import com.ticketing.system.Core.Application.services.CompanyAnalyticsService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.messaging.Conversation;
import com.ticketing.system.Core.Domain.messaging.ConversationType;
import com.ticketing.system.Core.Domain.messaging.IConversationRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.users.IUserRepository;

class CompanyAnalyticsServiceTest {

    private static final int COMPANY_ID = 10;
    // The company's two events; 999 belongs to a different company.
    private static final int EVENT_A = 100;
    private static final int EVENT_B = 101;
    private static final int OTHER_COMPANY_EVENT = 999;

    private IEventRepository eventRepository;
    private IOrderReceiptRepository orderReceiptRepository;
    private IConversationRepository conversationRepository;
    private  ITicketRepository ticketRepository;
    private  IProductionCompanyRepository companyRepository;
    private  IUserRepository userRepository;
    private CompanyAnalyticsService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(IEventRepository.class);
        orderReceiptRepository = mock(IOrderReceiptRepository.class);
        conversationRepository = mock(IConversationRepository.class);
        ticketRepository = mock(ITicketRepository.class);
        companyRepository = mock(IProductionCompanyRepository.class);
        userRepository = mock(IUserRepository.class);
        service = new CompanyAnalyticsService(eventRepository, orderReceiptRepository, conversationRepository, ticketRepository, companyRepository, userRepository);
    }

    private static ReceiptLine line(int ticketId, double price, int eventId) {
        return new ReceiptLine(ticketId, price, eventId, 1, null, LocalDateTime.now());
    }

    private static OrderReceipt receipt(boolean refunded, LocalDateTime purchasedAt, List<ReceiptLine> lines) {
        OrderReceipt r = mock(OrderReceipt.class);
        when(r.wasRefunded()).thenReturn(refunded);
        when(r.getPurchaseTime()).thenReturn(purchasedAt);
        when(r.getReceiptLines()).thenReturn(lines);
        return r;
    }

    private static Conversation conversation(ConversationType type, boolean closed) {
        Conversation c = mock(Conversation.class);
        when(c.getType()).thenReturn(type);
        when(c.isClosed()).thenReturn(closed);
        return c;
    }

    @Test
    void dashboard_aggregatesLiveCountersForTheCompany() {
        LocalDateTime now = LocalDateTime.now();

        when(eventRepository.findActiveByCompany(COMPANY_ID))
            .thenReturn(List.of(mock(Event.class), mock(Event.class))); // 2 ON_SALE
        when(eventRepository.findIdsByCompany(COMPANY_ID))
            .thenReturn(List.of(EVENT_A, EVENT_B));

        OrderReceipt mixed = receipt(false, now.minusDays(1),
            List.of(line(1, 50.0, EVENT_A), line(2, 30.0, OTHER_COMPANY_EVENT))); // only EVENT_A counts
        OrderReceipt twoTickets = receipt(false, now.minusDays(5),
            List.of(line(3, 20.0, EVENT_B), line(4, 20.0, EVENT_B)));
        OrderReceipt refunded = receipt(true, now.minusDays(2),
            List.of(line(5, 99.0, EVENT_A)));
        OrderReceipt tooOld = receipt(false, now.minusDays(45),
            List.of(line(6, 77.0, EVENT_A)));
        when(orderReceiptRepository.findByEventIds(List.of(EVENT_A, EVENT_B)))
            .thenReturn(List.of(mixed, twoTickets, refunded, tooOld));

        Conversation openInquiry = conversation(ConversationType.INQUIRY, false);    // counts
        Conversation closedInquiry = conversation(ConversationType.INQUIRY, true);   // closed → excluded
        Conversation complaint = conversation(ConversationType.COMPLAINT, false);    // wrong type → excluded
        when(conversationRepository.findByCompanyAsCounterparty(COMPANY_ID))
            .thenReturn(List.of(openInquiry, closedInquiry, complaint));

        CompanyDashboardDTO stats = service.dashboard(COMPANY_ID);

        assertEquals(2, stats.activeEvents());
        assertEquals(3, stats.ticketsSold30d());           // 1 (mixed) + 2 (twoTickets)
        assertEquals(90.0, stats.revenue30d());            // 50 + 20 + 20
        assertEquals(1, stats.openInquiries());
    }

    @Test
    void dashboard_freshCompanyWithNoEvents_returnsZeros() {
        when(eventRepository.findActiveByCompany(COMPANY_ID)).thenReturn(List.of());
        when(eventRepository.findIdsByCompany(COMPANY_ID)).thenReturn(List.of());
        when(conversationRepository.findByCompanyAsCounterparty(COMPANY_ID)).thenReturn(List.of());

        CompanyDashboardDTO stats = service.dashboard(COMPANY_ID);

        assertEquals(0, stats.activeEvents());
        assertEquals(0, stats.ticketsSold30d());
        assertEquals(0.0, stats.revenue30d());
        assertEquals(0, stats.openInquiries());
    }
}
