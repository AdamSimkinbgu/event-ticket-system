package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutAcceptanceTest {


    @Autowired private EventManagementService eventService;
    @Autowired private AuthenticationService authService;
    @Autowired private IProductionCompanyRepository companyRepository;
    @Autowired private IEventRepository eventRepository;
    @Autowired private ITicketRepository ticketRepository;
    @Autowired private IOrderReceiptRepository orderReceiptRepository;

    private static final AtomicInteger idGenerator = new AtomicInteger(5000);

    private AuthTokenDTO registerAndLoginUser(String username) {
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO(username, username + "@example.com", "Password123!", sid));
        return authService.login(new LoginRequestDTO(username, "Password123!", sid));
    }

    private int createRealCompany(int ownerId) {
        int companyId = idGenerator.getAndIncrement();
        ProductionCompany company = new ProductionCompany(companyId, ownerId, "Cancellation Test Co", 
                CompanyStatus.ACTIVE, "Acceptance Test", 5.0);
        companyRepository.updateCompany(company); 
        return companyId;
    }

    private int createRealEventWithSales(int companyId, int buyerId) {
        int eventId = idGenerator.getAndIncrement();
        
        InventoryZone zone = new InventoryZone(1, "General", 50, 100);
        VenueMap map = new VenueMap(1, new Location("Country", "City"), List.of(zone));
        Event event = new Event(eventId, "Test Concert", 5.0, List.of("Artist"), EventCategory.CONCERT, 
                companyId, EventStatus.SCHEDULED, map, List.of(), null, null);
        eventRepository.save(event);

        Ticket paidTicket = new Ticket(eventId, zone.getId(), 100.0, idGenerator.getAndIncrement(), "BARCODE1");
        paidTicket.setHolderUserId(buyerId);
        ticketRepository.save(paidTicket);

        Ticket availableTicket = new Ticket(eventId, zone.getId(), 100.0, idGenerator.getAndIncrement(), "BARCODE2");
        availableTicket.release(); // Set to AVAILABLE
        ticketRepository.save(availableTicket);

        ReceiptLine line = new ReceiptLine(paidTicket.getId(), 100.0, eventId, LocalDateTime.now());
        OrderReceipt receipt = OrderReceipt.forMember(buyerId, 100.0, List.of(line));
        orderReceiptRepository.save(receipt);

        return eventId;
    }

    @Test
    void GivenOwnerAndEventWithSales_WhenCancelEvent_ThenEventCanceledAndTicketsRefundedOrVoided() {
        AuthTokenDTO owner = registerAndLoginUser("eventOwner1");
        AuthTokenDTO buyer = registerAndLoginUser("ticketBuyer1");
        int companyId = createRealCompany(owner.userId());
        int eventId = createRealEventWithSales(companyId, buyer.userId());

        eventService.cancelEventAndRefund(owner.token(), eventId);

        Event dbEvent = eventRepository.findById(eventId);
        assertTrue(dbEvent.isCancelled(), "Event should be marked as canceled in the database");

        List<OrderReceipt> receipts = orderReceiptRepository.findByEventIds(String.valueOf(eventId));
        assertEquals(1, receipts.size(), "Should find 1 receipt for this event");
        assertTrue(receipts.get(0).wasRefunded(), "Order receipt should be marked as refunded");

        List<Ticket> dbTickets = ticketRepository.findByEventId(String.valueOf(eventId));
        assertEquals(2, dbTickets.size(), "Should find 2 tickets for this event");
        
        for (Ticket ticket : dbTickets) {
            if (ticket.getHolderUserId() != null && ticket.getHolderUserId() == buyer.userId()) {
                assertEquals(TicketStatus.REFUNDED, ticket.getStatus(), "Paid ticket should be marked REFUNDED");
            } else {
                assertEquals(TicketStatus.VOIDED, ticket.getStatus(), "Available/Unsold ticket should be marked VOIDED");
            }
        }
    }

    @Test
    void GivenUnauthorizedUser_WhenCancelEvent_ThenRejectedAndStateUnchanged() {
        AuthTokenDTO owner = registerAndLoginUser("eventOwner2");
        AuthTokenDTO randomUser = registerAndLoginUser("randomUser1");
        int companyId = createRealCompany(owner.userId());
        int eventId = createRealEventWithSales(companyId, owner.userId()); // owner acts as buyer here just for setup

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            eventService.cancelEventAndRefund(randomUser.token(), eventId);
        });
        
      
        Event dbEvent = eventRepository.findById(eventId);
        assertTrue(!dbEvent.isCancelled(), "Event should NOT be canceled");

        List<OrderReceipt> receipts = orderReceiptRepository.findByEventIds(String.valueOf(eventId));
        assertTrue(!receipts.get(0).wasRefunded(), "Order receipt should NOT be marked as refunded");
    }

    // UC-10
    @Test @Disabled("UC-10 main: full happy path — charge + issue + receipt + notify")
    void GivenValidOrder_WhenCheckout_ThenAllSucceed() {}
    @Test @Disabled("UC-10 negative: II.2.8.2 atomic — charge fails → no tickets issued")
    void GivenChargeFails_WhenCheckout_ThenAtomicAbort() {}
    @Test @Disabled("UC-10 negative: II.2.8.1 expired timer rejects")
    void GivenExpiredTimer_WhenCheckout_ThenRejected() {}
    @Test @Disabled("UC-10 negative: II.2.8.1 policy fails rejects")
    void GivenPolicyFails_WhenCheckout_ThenRejected() {}

    // UC-33
    @Test @Disabled("UC-33 main: payment gateway invoked with correct amount")
    void GivenSuccessfulCheckout_WhenCheckGateway_ThenChargeCalled() {}

    // UC-34
    @Test @Disabled("UC-34 main: ticket issuer invoked, barcodes received")
    void GivenSuccessfulCheckout_WhenCheckIssuer_ThenIssueCalled() {}

    // UC-4 (auto-refund)
    @Test @Disabled("UC-4 main: issuance fails → refund triggered automatically (I.3.3)")
    void GivenIssuanceFails_WhenCheckout_ThenRefundIssued() {}
    @Test @Disabled("UC-4 alt: event canceled → buyer notifications + refunds (I.3.3)")
    void GivenEventCanceled_WhenProcessed_ThenAllRefunded() {}
}
