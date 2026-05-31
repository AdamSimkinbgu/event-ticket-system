package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Application.services.SystemAdminService;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

class SystemAdminServiceTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final int ADMIN_USER_ID = 1;

    private ISessionManager sessionManager;
    private IAdminRepository adminRepository;
    private IOrderReceiptRepository orderReceiptRepository;
    private ITicketRepository ticketRepository;
    private IEventRepository eventRepository;
    private SystemAdminService service;


    @BeforeEach
    void setUp() {
        sessionManager = mock(ISessionManager.class);
        adminRepository = mock(IAdminRepository.class);
        orderReceiptRepository = mock(IOrderReceiptRepository.class);
        ticketRepository = mock(ITicketRepository.class);
        eventRepository = mock(IEventRepository.class);
        service = new SystemAdminService(
                sessionManager,
                adminRepository,
                orderReceiptRepository,
                ticketRepository,
                eventRepository,
                List.of(),
                List.of()
        );
        when(sessionManager.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(ADMIN_TOKEN)).thenReturn(ADMIN_USER_ID);
        when(adminRepository.findById(ADMIN_USER_ID)).thenReturn(new Admin(ADMIN_USER_ID, "admin", "hash", true));
    }

    @Test @Disabled("UC-1: I.1.1 verify invariants on startup")
    void givenValidState_whenInitializePlatform_thenSucceeds() {}

    @Test @Disabled("UC-1: I.1.2 missing payment gateway → market does not open")
    void givenNoPaymentGateway_whenInitializePlatform_thenFails() {}

    @Test @Disabled("UC-1: I.1.3 missing ticket issuer → market does not open")
    void givenNoTicketIssuer_whenInitializePlatform_thenFails() {}

    @Test @Disabled("UC-1: I.1.4 no admin → auto-generates default")
    void givenNoAdmin_whenInitializePlatform_thenDefaultAdminCreated() {}

    @Test @Disabled("UC-32: openMarket re-runs all verifications + flips state")
    void givenInitializedPlatform_whenOpenMarket_thenStateOpen() {}

    @Test @Disabled("UC-31: viewGlobalHistory admin-only RBAC")
    void givenNonAdmin_whenViewGlobalHistory_thenRejected() {}

    // --- UC-31: viewGlobalHistory ---

    @Test
    void givenNoMatchingReceipts_whenViewGlobalHistory_thenReturnsEmptyRecords() {
        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(null, null, null, null, null);
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of());

        List<PurchaseHistoryDTO> result = service.viewGlobalHistory(ADMIN_TOKEN, filters);

        assertEquals(1, result.size());
        assertTrue(result.get(0).records().isEmpty());
    }

    @Test
    void givenOneReceipt_whenViewGlobalHistory_thenRecordMapped() {
        int ticketId = 42;
        double price = 99.0;
        OrderReceipt receipt = OrderReceipt.forMember(11, 1, price, List.of());
        Ticket ticket = new Ticket(receipt.geteventId(), 1, 11, price, ticketId, "BARCODE");
        Event event = mock(Event.class);
        when(event.getName()).thenReturn("Rock Night");

        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(1, null, null, null, null);
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of(receipt));
        when(ticketRepository.findByOrderReceiptId(receipt.getId())).thenReturn(List.of(ticket));
        when(eventRepository.findById(receipt.geteventId())).thenReturn(event);

        List<PurchaseHistoryDTO> result = service.viewGlobalHistory(ADMIN_TOKEN, filters);

        assertEquals(1, result.size());
        List<PurchaseHistoryDTO.PurchaseRecordDTO> records = result.get(0).records();
        assertEquals(1, records.size());
        PurchaseHistoryDTO.PurchaseRecordDTO record = records.get(0);
        assertEquals("Rock Night", record.eventName());
        assertEquals(price, record.totalPaid());
        assertEquals(1, record.tickets().size());
        assertEquals(ticketId, record.tickets().get(0).ticketId());
    }

    @Test
    void givenMultipleReceipts_whenViewGlobalHistory_thenAllRecordsMapped() {
        OrderReceipt receipt1 = OrderReceipt.forMember(11, 1, 50.0, List.of());
        OrderReceipt receipt2 = OrderReceipt.forMember(12, 2, 75.0, List.of());
        Event event = mock(Event.class);
        when(event.getName()).thenReturn("Jazz Night");

        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(null, null, null, null, null);
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of(receipt1, receipt2));
        when(ticketRepository.findByOrderReceiptId(anyInt())).thenReturn(List.of());
        when(eventRepository.findById(anyInt())).thenReturn(event);

        List<PurchaseHistoryDTO> result = service.viewGlobalHistory(ADMIN_TOKEN, filters);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).records().size());
    }

    @Test
    void givenFilters_whenViewGlobalHistory_thenFiltersPassedToRepository() {
        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(
                7, 3, List.of("10", "11"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of());

        service.viewGlobalHistory(ADMIN_TOKEN, filters);

        verify(orderReceiptRepository).findGlobal(filters);
    }

    @Test
    void givenReceiptWithMultipleTickets_whenViewGlobalHistory_thenTotalPaidIsSumOfTicketPrices() {
        OrderReceipt receipt = OrderReceipt.forMember(11, 1, 0.0, List.of());
        Ticket t1 = new Ticket(receipt.geteventId(), 1, 11, 30.0, 1, "B1");
        Ticket t2 = new Ticket(receipt.geteventId(), 1, 11, 45.0, 2, "B2");
        Event event = mock(Event.class);
        when(event.getName()).thenReturn("Pop Show");

        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(null, null, null, null, null);
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of(receipt));
        when(ticketRepository.findByOrderReceiptId(receipt.getId())).thenReturn(List.of(t1, t2));
        when(eventRepository.findById(anyInt())).thenReturn(event);

        List<PurchaseHistoryDTO> result = service.viewGlobalHistory(ADMIN_TOKEN, filters);

        PurchaseHistoryDTO.PurchaseRecordDTO record = result.get(0).records().get(0);
        assertEquals(75.0, record.totalPaid());
        assertEquals(2, record.tickets().size());
    }
}
