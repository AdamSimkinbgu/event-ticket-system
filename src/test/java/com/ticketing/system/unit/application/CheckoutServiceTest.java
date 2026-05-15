package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ticketing.system.Core.Application.dto.BarcodeDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.dto.GuestCheckoutContactDTO;
import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;
import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.exceptions.GuestCheckoutMissingContactException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Core.Domain.users.Session;

class CheckoutServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private IActiveOrderRepository mockActiveOrderRepo;
    private IEventRepository mockEventRepo;
    private ITicketRepository mockTicketRepo;
    private IOrderReceiptRepository mockOrderReceiptRepo;
    private ITicketIssuer mockTicketIssuer;
    private IPaymentGateway mockPaymentGateway;
    private INotificationService mockNotificationService;
    private ISessionManager mockiSessionManager;
    private ISessionRepository mockSessionRepo;
    private Clock fixedClock;

    private CheckoutService checkoutService;

    private final String VALID_TOKEN = "valid-token";
    private final String INVALID_TOKEN = "invalid-token";

    private final int USER_ID = 1;

    private final int EVENT_ID_1 = 1;
    private final int EVENT_ID_2 = 2;

    private final int ZONE_ID_1 = 10;
    private final int ZONE_ID_2 = 20;
    private final int ZONE_ID_3 = 30;

    private final int TICKET_ID_1 = 101;
    private final int TICKET_ID_2 = 102;
    private final int TICKET_ID_3 = 103;

    private final int PAYMENT_TRANSACTION_ID = 201;
    private final String ISSUANCE_TRANSACTION_ID = "issue-tx-1";

    private PaymentRequestDTO paymentRequest;

    private ActiveOrder mockOrder;
    private CartLineItem itemEvent1Zone1;
    private CartLineItem itemEvent1Zone2;
    private CartLineItem itemEvent2Zone3;
    private Event event1;
    private Event event2;

    @BeforeEach
    void setUp() {
        mockActiveOrderRepo = mock(IActiveOrderRepository.class);
        mockEventRepo = mock(IEventRepository.class);
        mockTicketRepo = mock(ITicketRepository.class);
        mockOrderReceiptRepo = mock(IOrderReceiptRepository.class);
        mockTicketIssuer = mock(ITicketIssuer.class);
        mockPaymentGateway = mock(IPaymentGateway.class);
        mockNotificationService = mock(INotificationService.class);
        mockiSessionManager = mock(ISessionManager.class);
        mockSessionRepo = mock(ISessionRepository.class);
        fixedClock = Clock.fixed(T0, ZoneOffset.UTC);


        checkoutService = new CheckoutService(
                mockActiveOrderRepo,
                mockEventRepo,
                mockTicketRepo,
                mockOrderReceiptRepo,
                mockTicketIssuer,
                mockPaymentGateway,
                mockNotificationService,
                mockiSessionManager,
                mockSessionRepo,
                fixedClock
        );

        paymentRequest = new PaymentRequestDTO(
                "idem-key-1",
                0,
                "ILS",
                "payment-token",
                Integer.valueOf(USER_ID),
                null
        );

        mockOrder = mock(ActiveOrder.class);

        itemEvent1Zone1 = mock(CartLineItem.class);
        itemEvent1Zone2 = mock(CartLineItem.class);
        itemEvent2Zone3 = mock(CartLineItem.class);

        event1 = mock(Event.class);
        event2 = mock(Event.class);

        when(mockiSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockiSessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(mockOrder);

        when(itemEvent1Zone1.geteventId()).thenReturn(EVENT_ID_1);
        when(itemEvent1Zone1.getzoneId()).thenReturn(ZONE_ID_1);
        when(itemEvent1Zone1.getPriceAtReservation()).thenReturn(100.0);

        when(itemEvent1Zone2.geteventId()).thenReturn(EVENT_ID_1);
        when(itemEvent1Zone2.getzoneId()).thenReturn(ZONE_ID_2);
        when(itemEvent1Zone2.getPriceAtReservation()).thenReturn(150.0);

        when(itemEvent2Zone3.geteventId()).thenReturn(EVENT_ID_2);
        when(itemEvent2Zone3.getzoneId()).thenReturn(ZONE_ID_3);
        when(itemEvent2Zone3.getPriceAtReservation()).thenReturn(200.0);

        when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
        when(mockEventRepo.findById(EVENT_ID_2)).thenReturn(event2);

        when(event1.getName()).thenReturn("Concert");
        when(event2.getName()).thenReturn("Theater");
    }

    @Test
    void GivenMissingToken_WhenCheckout_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout("", paymentRequest)
        );
    }

    @Test
    void GivenNullToken_WhenCheckout_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(null, paymentRequest)
        );
    }

    @Test
    void GivenInvalidToken_WhenCheckout_ThenThrowException() {
        when(mockiSessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(INVALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenNoActiveOrder_WhenCheckout_ThenThrowException() {
        when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenExpiredOrderWithMultipleTickets_WhenCheckout_ThenThrowException() {
        when(mockOrder.validateCanCheckout()).thenReturn(false);
        when(mockOrder.ReturnToStock()).thenReturn(
                List.of(itemEvent1Zone1, itemEvent1Zone2, itemEvent2Zone3)
        );

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenOrderCannotCheckout_WhenCheckout_ThenThrowException() {
        when(mockOrder.validateCanCheckout()).thenReturn(false);
        when(mockOrder.ReturnToStock()).thenReturn(List.of());

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenPaymentFails_WhenCheckout_ThenThrowException() {
        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));
        when(mockOrder.ReturnToStock()).thenReturn(List.of(itemEvent1Zone1));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);
        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenTicketIssuanceFailsAfterPayment_WhenCheckout_ThenThrowException() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                100.0,
                "ILS",
                LocalDateTime.now()//////////////////////////////////////////////////
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));
        when(mockOrder.ReturnToStock()).thenReturn(List.of(itemEvent1Zone1));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenTicketIssuanceReturnsNullBarcodes_WhenCheckout_ThenThrowException() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                100.0,
                "ILS",
                LocalDateTime.now()///////////////////////////////////////////////
        );

        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                null
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));
        when(mockOrder.ReturnToStock()).thenReturn(List.of(itemEvent1Zone1));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenTicketIssuanceReturnsEmptyBarcodes_WhenCheckout_ThenThrowException() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                100.0,
                "ILS",
                LocalDateTime.now()
        );//////////////////////////////////////////////////////////////////////////////////////////////////

        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                List.of()
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));
        when(mockOrder.ReturnToStock()).thenReturn(List.of(itemEvent1Zone1));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenMultipleTicketsButIssuerReturnsLessBarcodes_WhenCheckout_ThenThrowException() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                450.0,
                "ILS",
                LocalDateTime.now()
        );
///////////////////////////////////////////////////////////////////////////////////////////////////////////
        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                List.of(new BarcodeDTO(TICKET_ID_1, "barcode-1", "QR"))
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(
                List.of(itemEvent1Zone1, itemEvent1Zone2, itemEvent2Zone3)
        );
        when(mockOrder.ReturnToStock()).thenReturn(
                List.of(itemEvent1Zone1, itemEvent1Zone2, itemEvent2Zone3)
        );

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(250.0);
        when(event2.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(200.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenPaymentRefundFails_WhenCheckout_ThenThrowRefundException() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                100.0,
                "ILS",
                LocalDateTime.now()
        );//////////////////////////////////////////////////////////////////////////////////

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));
        when(mockOrder.ReturnToStock()).thenReturn(List.of(itemEvent1Zone1));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(null);

        doThrow(new RuntimeException("refund failed"))
                .when(mockPaymentGateway)
                .refund(PAYMENT_TRANSACTION_ID, 100.0);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenTicketFromDifferentEventDoesNotExist_WhenCheckout_ThenThrowException() {
        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1, itemEvent2Zone3));
        when(mockOrder.ReturnToStock()).thenReturn(List.of(itemEvent1Zone1, itemEvent2Zone3));

        when(mockEventRepo.findById(EVENT_ID_2)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, paymentRequest)
        );
    }

    @Test
    void GivenValidCheckout_WhenCheckout_ThenReturnCheckoutResult() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                100.0,
                "ILS",
                LocalDateTime.now()
        );

        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                List.of(new BarcodeDTO(TICKET_ID_1, "barcode-value-1", "QR"))
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        CheckoutResultDTO result = checkoutService.checkout(VALID_TOKEN, paymentRequest);

        assertEquals(
                new CheckoutResultDTO(
                        100.0,
                        PAYMENT_TRANSACTION_ID,
                        List.of(TICKET_ID_1)
                ),
                result
        );
    }

    @Test
    void GivenValidCheckoutWithCalculatedPrice_WhenCheckout_ThenReturnCalculatedTotal() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                150.0,
                "ILS",
                LocalDateTime.now()
        );

        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                List.of(new BarcodeDTO(TICKET_ID_2, "barcode-value-2", "QR"))
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone2));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(150.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        CheckoutResultDTO result = checkoutService.checkout(VALID_TOKEN, paymentRequest);

        assertEquals(150.0, result.totalCharged());
    }

    @Test
    void GivenMultipleTicketsFromDifferentZonesSameEvent_WhenCheckout_ThenBuyAllTickets() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                250.0,
                "ILS",
                LocalDateTime.now()
        );

        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                List.of(
                        new BarcodeDTO(TICKET_ID_1, "barcode-zone-1", "QR"),
                        new BarcodeDTO(TICKET_ID_2, "barcode-zone-2", "QR")
                )
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1, itemEvent1Zone2));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(250.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        CheckoutResultDTO result = checkoutService.checkout(VALID_TOKEN, paymentRequest);

        assertEquals(250.0, result.totalCharged());
        assertEquals(List.of(TICKET_ID_1, TICKET_ID_2), result.issuedTicketIds());
    }

    @Test
    void GivenTicketsFromDifferentEvents_WhenCheckout_ThenBuyAllTickets() {
        PaymentResultDTO paymentResult = new PaymentResultDTO(
                PAYMENT_TRANSACTION_ID,
                "gateway",
                300.0,
                "ILS",
                LocalDateTime.now()
        );

        IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                ISSUANCE_TRANSACTION_ID,
                "issuer",
                LocalDateTime.now(),
                List.of(
                        new BarcodeDTO(TICKET_ID_1, "barcode-event-1", "QR"),
                        new BarcodeDTO(TICKET_ID_3, "barcode-event-2", "QR")
                )
        );

        when(mockOrder.validateCanCheckout()).thenReturn(true);
        when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1, itemEvent2Zone3));

        when(event1.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(100.0);
        when(event2.calculatePrice(any(), any(LocalDateTime.class))).thenReturn(200.0);

        when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
        when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

        CheckoutResultDTO result = checkoutService.checkout(VALID_TOKEN, paymentRequest);

        assertEquals(300.0, result.totalCharged());
        assertEquals(List.of(TICKET_ID_1, TICKET_ID_3), result.issuedTicketIds());

        // D7: Member tickets carry the buyer's userId for UC-16 fast lookup.
        org.mockito.ArgumentCaptor<com.ticketing.system.Core.Domain.Tickets.Ticket> savedTickets =
                org.mockito.ArgumentCaptor.forClass(com.ticketing.system.Core.Domain.Tickets.Ticket.class);
        org.mockito.Mockito.verify(mockTicketRepo, org.mockito.Mockito.times(2)).save(savedTickets.capture());
        for (com.ticketing.system.Core.Domain.Tickets.Ticket t : savedTickets.getAllValues()) {
            assertEquals(Integer.valueOf(USER_ID), t.getHolderUserId());
        }
    }

    // ---------------------------------------------------------------------
    // Guest checkout (D5 reversed)
    // ---------------------------------------------------------------------

    private static final String GUEST_SID = "guest-sid-abc";
    private static final GuestCheckoutContactDTO VALID_CONTACT =
            new GuestCheckoutContactDTO("alice@example.com", "Alice");

    private Session mockValidGuestSession(String sid) {
        Session guest = new Session(sid, null, T0, T0.plusSeconds(3600));
        when(mockSessionRepo.findById(sid)).thenReturn(Optional.of(guest));
        return guest;
    }

    @Test
    void givenNullSessionId_whenCheckoutAsGuest_thenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                checkoutService.checkoutAsGuest(null, VALID_CONTACT, paymentRequest));
    }

    @Test
    void givenBlankSessionId_whenCheckoutAsGuest_thenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                checkoutService.checkoutAsGuest("   ", VALID_CONTACT, paymentRequest));
    }

    @Test
    void givenNullContact_whenCheckoutAsGuest_thenMissingContactException() {
        assertThrows(GuestCheckoutMissingContactException.class, () ->
                checkoutService.checkoutAsGuest(GUEST_SID, null, paymentRequest));
    }

    @Test
    void givenMalformedEmail_whenCheckoutAsGuest_thenMissingContactException() {
        GuestCheckoutContactDTO bad = new GuestCheckoutContactDTO("not-an-email", "Alice");
        assertThrows(GuestCheckoutMissingContactException.class, () ->
                checkoutService.checkoutAsGuest(GUEST_SID, bad, paymentRequest));
    }

    @Test
    void givenBlankName_whenCheckoutAsGuest_thenMissingContactException() {
        GuestCheckoutContactDTO bad = new GuestCheckoutContactDTO("alice@example.com", "  ");
        assertThrows(GuestCheckoutMissingContactException.class, () ->
                checkoutService.checkoutAsGuest(GUEST_SID, bad, paymentRequest));
    }

    @Test
    void givenUnknownSessionId_whenCheckoutAsGuest_thenInvalidTokenException() {
        when(mockSessionRepo.findById("ghost")).thenReturn(Optional.empty());
        assertThrows(InvalidTokenException.class, () ->
                checkoutService.checkoutAsGuest("ghost", VALID_CONTACT, paymentRequest));
    }

    @Test
    void givenMemberSessionId_whenCheckoutAsGuest_thenInvalidTokenException() {
        Session member = new Session("sid", 5, T0, T0.plusSeconds(3600));
        when(mockSessionRepo.findById("sid")).thenReturn(Optional.of(member));
        assertThrows(InvalidTokenException.class, () ->
                checkoutService.checkoutAsGuest("sid", VALID_CONTACT, paymentRequest));
    }

    @Test
    void givenExpiredSession_whenCheckoutAsGuest_thenSessionExpiredException() {
        Session expired = new Session("sid", null, T0.minusSeconds(7200), T0.minusSeconds(60));
        when(mockSessionRepo.findById("sid")).thenReturn(Optional.of(expired));
        assertThrows(SessionExpiredException.class, () ->
                checkoutService.checkoutAsGuest("sid", VALID_CONTACT, paymentRequest));
    }

    @Test
    void givenNoCartForSession_whenCheckoutAsGuest_thenIllegalStateException() {
        mockValidGuestSession(GUEST_SID);
        when(mockActiveOrderRepo.getBySessionId(GUEST_SID)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () ->
                checkoutService.checkoutAsGuest(GUEST_SID, VALID_CONTACT, paymentRequest));
    }

    @Test
    void givenMissingPaymentRequest_whenCheckoutAsGuest_thenIllegalArgumentException() {
        // Validation order: contact OK, then payment request null.
        mockValidGuestSession(GUEST_SID);
        assertThrows(IllegalArgumentException.class, () ->
                checkoutService.checkoutAsGuest(GUEST_SID, VALID_CONTACT, null));
    }

    @Test
    void givenMissingIdempotencyKey_whenCheckoutAsGuest_thenIllegalArgumentException() {
        mockValidGuestSession(GUEST_SID);
        PaymentRequestDTO bad = new PaymentRequestDTO(null, 0, "ILS", "payment-token", null, "alice@example.com");
        assertThrows(IllegalArgumentException.class, () ->
                checkoutService.checkoutAsGuest(GUEST_SID, VALID_CONTACT, bad));
    }
}