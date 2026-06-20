package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Application.services.SystemAdminService;
import com.ticketing.system.Core.Application.services.SystemIntegrityVerifier;
import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.exceptions.ExternalServiceUnavailableException;
import com.ticketing.system.Core.Domain.exceptions.MissingDefaultAdminException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;

class SystemAdminServiceTest {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final int ADMIN_USER_ID = 1;

    private ISessionManager sessionManager;
    private IAdminRepository adminRepository;
    private IOrderReceiptRepository orderReceiptRepository;
    private ITicketRepository ticketRepository;
    private IEventRepository eventRepository;
    private IPasswordHasher passwordHasher;
    private SystemIntegrityVerifier integrityVerifier;
    private SystemAdminService service;


    @BeforeEach
    void setUp() {
        sessionManager = mock(ISessionManager.class);
        adminRepository = mock(IAdminRepository.class);
        orderReceiptRepository = mock(IOrderReceiptRepository.class);
        ticketRepository = mock(ITicketRepository.class);
        eventRepository = mock(IEventRepository.class);
        passwordHasher = mock(IPasswordHasher.class);
        integrityVerifier = mock(SystemIntegrityVerifier.class);
        when(passwordHasher.hash(anyString())).thenReturn("hashed-password");
        service = new SystemAdminService(
                sessionManager,
                adminRepository,
                orderReceiptRepository,
                ticketRepository,
                eventRepository,
                List.of(),
                List.of(),
                passwordHasher,
                integrityVerifier,
                "admin",
                "admin"
        );
        when(sessionManager.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(ADMIN_TOKEN)).thenReturn(ADMIN_USER_ID);
        when(adminRepository.findById(ADMIN_USER_ID)).thenReturn(new Admin(ADMIN_USER_ID, "admin", "hash", true));
    }

    // --- UC-1: initializePlatform ---

    @Test
    void givenAllServicesReachableAndAdminExists_whenInitializePlatform_thenSucceeds() {
        when(adminRepository.existsAny()).thenReturn(true);
        SystemAdminService svc = serviceWith(List.of(reachablePayment()), List.of(reachableIssuer()));
        assertDoesNotThrow(svc::initializePlatform);
    }

    @Test
    void givenNoReachablePaymentGateway_whenInitializePlatform_thenThrows() {
        SystemAdminService svc = serviceWith(List.of(), List.of(reachableIssuer()));
        assertThrows(ExternalServiceUnavailableException.class, svc::initializePlatform);
    }

    @Test
    void givenNoReachableTicketIssuer_whenInitializePlatform_thenThrows() {
        SystemAdminService svc = serviceWith(List.of(reachablePayment()), List.of());
        assertThrows(ExternalServiceUnavailableException.class, svc::initializePlatform);
    }

    @Test
    void givenNoAdmin_whenInitializePlatform_thenDefaultAdminCreated() {
        when(adminRepository.existsAny()).thenReturn(false, true);
        SystemAdminService svc = serviceWith(List.of(reachablePayment()), List.of(reachableIssuer()));
        svc.initializePlatform();
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void givenNoAdminAndCreationDoesNotPersist_whenInitializePlatform_thenThrows() {
        // existsAny() stays false even after save() → auto-creation failed.
        when(adminRepository.existsAny()).thenReturn(false);
        SystemAdminService svc = serviceWith(List.of(reachablePayment()), List.of(reachableIssuer()));
        assertThrows(MissingDefaultAdminException.class, svc::initializePlatform);
    }

    @Test
    void givenAlreadyInitialized_whenInitializePlatformAgain_thenSecondCallIsNoOp() {
        when(adminRepository.existsAny()).thenReturn(true);
        SystemAdminService svc = serviceWith(List.of(reachablePayment()), List.of(reachableIssuer()));
        svc.initializePlatform();
        assertDoesNotThrow(svc::initializePlatform);
    }

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
        int ticketId = 1;
        double price = 99.0;
        ReceiptLine line1 = new ReceiptLine(1, 25.0, 25, 1, null, LocalDateTime.now());
        OrderReceipt receipt = OrderReceipt.forMember(11, 1, price, List.of(line1));
        Ticket ticket = new Ticket(1, 1, 11, null, price, ticketId, "BARCODE");
        Event event = mock(Event.class);
        when(event.getName()).thenReturn("Rock Night");

        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(1, null, null, null, null);
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of(receipt));
        when(ticketRepository.findByOrderReceiptId(receipt.getId())).thenReturn(List.of(ticket));
        when(eventRepository.findById(1)).thenReturn(event);

        List<PurchaseHistoryDTO> result = service.viewGlobalHistory(ADMIN_TOKEN, filters);

        assertEquals(1, result.size());
        List<PurchaseHistoryDTO.PurchaseRecordDTO> records = result.get(0).records();
        assertEquals(1, records.size());
        PurchaseHistoryDTO.PurchaseRecordDTO record = records.get(0);
        assertEquals(price, record.totalPaid());
        assertEquals(1, record.tickets().size());
        assertEquals(ticketId, record.tickets().get(0).ticketId());
    }




    @Test
    void givenMultipleReceipts_whenViewGlobalHistory_thenAllRecordsMapped() {
        ReceiptLine line1 = new ReceiptLine(1, 25.0, 25, 1, null, LocalDateTime.now());
        ReceiptLine line2 = new ReceiptLine(2, 25.0, 3,  1, "A2", LocalDateTime.now());
        OrderReceipt receipt1 = OrderReceipt.forMember(11, 1, 50.0, List.of(line1));
        OrderReceipt receipt2 = OrderReceipt.forMember(12, 2, 75.0, List.of(line2));
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
                7, 3, List.of(10, 11),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        // normalizeGlobalHistoryFilters resolves companyId → null and intersects eventIds
        GlobalHistoryFiltersDTO expectedFilters = new GlobalHistoryFiltersDTO(
                7, null, List.of(10, 11),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31));
        when(eventRepository.findIdsByCompany(3)).thenReturn(List.of(10, 11));
        when(orderReceiptRepository.findGlobal(expectedFilters)).thenReturn(List.of());

        service.viewGlobalHistory(ADMIN_TOKEN, filters);

        verify(orderReceiptRepository).findGlobal(expectedFilters);
    }

    @Test
    void givenReceiptWithMultipleTickets_whenViewGlobalHistory_thenTotalPaidIsSumOfTicketPrices() {
        ReceiptLine line1 = new ReceiptLine(1, 50.0, 25, 1, null, LocalDateTime.now());
        ReceiptLine line2 = new ReceiptLine(2, 25.0, 3,  1, "A2", LocalDateTime.now());
        OrderReceipt receipt = OrderReceipt.forMember(11, 1, 75.0, List.of(line1, line2));
        Ticket t1 = new Ticket(1, 1, 11, null, 30.0, 1, "B1");
        Ticket t2 = new Ticket(1, 1, 11, null, 45.0, 2, "B2");
        Event event = mock(Event.class);
        when(event.getName()).thenReturn("Pop Show");

        GlobalHistoryFiltersDTO filters = new GlobalHistoryFiltersDTO(null, null, null, null, null);
        when(orderReceiptRepository.findGlobal(filters)).thenReturn(List.of(receipt));
        when(ticketRepository.findByOrderReceiptId(1)).thenReturn(List.of(t1, t2));
        when(eventRepository.findById(anyInt())).thenReturn(event);

        List<PurchaseHistoryDTO> result = service.viewGlobalHistory(ADMIN_TOKEN, filters);

        PurchaseHistoryDTO.PurchaseRecordDTO record = result.get(0).records().get(0);
        assertEquals(75.0, record.totalPaid());
        assertEquals(2, record.tickets().size());
    }


    // --- helpers ---

    private SystemAdminService serviceWith(List<IPaymentGateway> gateways, List<ITicketIssuer> issuers) {
        return new SystemAdminService(
                sessionManager, adminRepository, orderReceiptRepository,
                ticketRepository, eventRepository, gateways, issuers, passwordHasher,
                integrityVerifier, "admin", "admin");
    }

    private IPaymentGateway reachablePayment() {
        IPaymentGateway gateway = mock(IPaymentGateway.class);
        when(gateway.verifyConnection()).thenReturn(true);
        return gateway;
    }

    private ITicketIssuer reachableIssuer() {
        ITicketIssuer issuer = mock(ITicketIssuer.class);
        when(issuer.verifyConnection()).thenReturn(true);
        return issuer;
    }
}
