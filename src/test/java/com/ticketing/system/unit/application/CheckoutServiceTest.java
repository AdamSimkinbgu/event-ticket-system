package com.ticketing.system.unit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import com.ticketing.system.Core.Application.dto.BarcodeDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;
import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

import static org.junit.jupiter.api.Assertions.*;

import com.ticketing.system.Core.Domain.events.DiscountPolicy;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.PurchasePolicy;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.VenueMap;

class CheckoutServiceTest {

    private IActiveOrderRepository mockActiveOrderRepo;
    private IEventRepository mockEventRepo;
    private ITicketRepository mockTicketRepo;
    private IOrderReceiptRepository mockOrderReceiptRepo;
    private ITicketIssuer mockTicketIssuer;
    private IPaymentGateway mockPaymentGateway;
    private INotificationService mockNotificationService;
    private ISessionManager mockiSessionManager;

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

    private final String IDEMPOTENCY_KEY = "idem-key-1";
    private final String CURRENCY = "ILS";
    private final String PAYMENT_METHOD_TOKEN = "payment-token";

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
        AtomicInteger receiptIds = new AtomicInteger(1);
        when(mockOrderReceiptRepo.nextId()).thenAnswer(invocation -> receiptIds.getAndIncrement());

        mockTicketIssuer = mock(ITicketIssuer.class);
        mockPaymentGateway = mock(IPaymentGateway.class);
        mockNotificationService = mock(INotificationService.class);
        mockiSessionManager = mock(ISessionManager.class);

        checkoutService = new CheckoutService(
                mockActiveOrderRepo,
                mockEventRepo,
                mockTicketRepo,
                mockOrderReceiptRepo,
                mockTicketIssuer,
                mockPaymentGateway,
                mockNotificationService,
                mockiSessionManager
        );

        mockOrder = mock(ActiveOrder.class);

        itemEvent1Zone1 = mock(CartLineItem.class);
        itemEvent1Zone2 = mock(CartLineItem.class);
        itemEvent2Zone3 = mock(CartLineItem.class);

        event1 = mock(Event.class, RETURNS_DEEP_STUBS);
        event2 = mock(Event.class, RETURNS_DEEP_STUBS);

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
                checkoutService.checkout("", IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN)
        );
    }

    @Test
    void GivenNullToken_WhenCheckout_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(null, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN)
        );
    }

    @Test
    void GivenInvalidToken_WhenCheckout_ThenThrowException() {
        when(mockiSessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(INVALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN)
        );
    }

    @Test
    void GivenNoActiveOrder_WhenCheckout_ThenThrowException() {
        when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN)
        );
    }

    @Test
    void GivenExpiredOrderWithMultipleTickets_WhenCheckout_ThenThrowException() {
        when(mockOrder.validateCanCheckout()).thenReturn(false);
        when(mockOrder.ReturnToStock()).thenReturn(
                List.of(itemEvent1Zone1, itemEvent1Zone2, itemEvent2Zone3)
        );

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN)
        );
    }
    //////////////////////////////////////////////////////////added test for expired order with multiple tickets scenario after check1
@Test
void GivenExpiredOrderWithMultipleTickets_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone1 = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    InventoryZone zone2 = new StandingZone(ZONE_ID_2, "REGULAR", 5, 150.0);

    zone1.reserve(InventorySelectionDTO.standing(1));
    zone2.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(EVENT_ID_1, ZONE_ID_1, 1, 100.0, LocalDateTime.now().minusMinutes(20));
    activeOrder.addStandingReservation(EVENT_ID_1, ZONE_ID_2, 1, 150.0, LocalDateTime.now());

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone1.release(InventorySelectionDTO.standing(1));
        return true;
    });

    when(event1.releaseInventory(ZONE_ID_2, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone2.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_2));

    assertEquals(5, zone1.getAvailableAmount());
    assertEquals(5, zone2.getAvailableAmount());
    assertEquals(0, zone1.getReservedAmount());
    assertEquals(0, zone2.getReservedAmount());
}



    @Test
    void GivenOrderCannotCheckout_WhenCheckout_ThenThrowException() {
        when(mockOrder.validateCanCheckout()).thenReturn(false);
        when(mockOrder.ReturnToStock()).thenReturn(List.of());

        assertThrows(RuntimeException.class, () ->
                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN)
        );
    }

    @Test
void GivenOrderCannotCheckout_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    zone.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(
            EVENT_ID_1,
            ZONE_ID_1,
            1,
            100.0,
            LocalDateTime.now().minusMinutes(20)
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);
    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(5, zone.getAvailableAmount());
    assertEquals(0, zone.getReservedAmount());
}

   @Test
void GivenPaymentFails_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    zone.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(
            EVENT_ID_1,
            ZONE_ID_1,
            1,
            100.0,
            LocalDateTime.now()
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);
    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone);

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(null);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(5, zone.getAvailableAmount());
    assertEquals(0, zone.getReservedAmount());
}
    @Test
void GivenTicketIssuanceFailsAfterPayment_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    zone.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(
            EVENT_ID_1,
            ZONE_ID_1,
            1,
            100.0,
            LocalDateTime.now()
    );

    PaymentResultDTO paymentResult = new PaymentResultDTO(
            PAYMENT_TRANSACTION_ID,
            "gateway",
            100.0,
            "ILS",
            LocalDateTime.now()
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone);

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(null);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(5, zone.getAvailableAmount());
    assertEquals(0, zone.getReservedAmount());
}

   @Test
void GivenTicketIssuanceReturnsNullBarcodes_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    zone.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(
            EVENT_ID_1,
            ZONE_ID_1,
            1,
            100.0,
            LocalDateTime.now()
    );

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
            null
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone);

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(issuanceResult);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(5, zone.getAvailableAmount());
    assertEquals(0, zone.getReservedAmount());
}@Test
void GivenTicketIssuanceReturnsEmptyBarcodes_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    zone.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(
            EVENT_ID_1,
            ZONE_ID_1,
            1,
            100.0,
            LocalDateTime.now()
    );

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
            List.of()
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone);

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(issuanceResult);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(5, zone.getAvailableAmount());
    assertEquals(0, zone.getReservedAmount());
}

   @Test
void GivenMultipleTicketsButIssuerReturnsLessBarcodes_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone1 = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    InventoryZone zone2 = new StandingZone(ZONE_ID_2, "REGULAR", 5, 150.0);
    InventoryZone zone3 = new StandingZone(ZONE_ID_3, "BALCONY", 5, 200.0);

    zone1.reserve(InventorySelectionDTO.standing(1));
    zone2.reserve(InventorySelectionDTO.standing(1));
    zone3.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(EVENT_ID_1, ZONE_ID_1, 1, 100.0, LocalDateTime.now());
    activeOrder.addStandingReservation(EVENT_ID_1, ZONE_ID_2, 1, 150.0, LocalDateTime.now());
    activeOrder.addStandingReservation(EVENT_ID_2, ZONE_ID_3, 1, 200.0, LocalDateTime.now());

    PaymentResultDTO paymentResult = new PaymentResultDTO(
            PAYMENT_TRANSACTION_ID,
            "gateway",
            450.0,
            "ILS",
            LocalDateTime.now()
    );

    IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
            ISSUANCE_TRANSACTION_ID,
            "issuer",
            LocalDateTime.now(),
            List.of(new BarcodeDTO(TICKET_ID_1, "barcode-1", "QR"))
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(mockEventRepo.findById(EVENT_ID_2)).thenReturn(event2);

    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone1);
    when(event1.getVenueMap().getZone(ZONE_ID_2)).thenReturn(zone2);
    when(event2.getVenueMap().getZone(ZONE_ID_3)).thenReturn(zone3);

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(250.0);
    when(event2.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(200.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(issuanceResult);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone1.release(InventorySelectionDTO.standing(1));
        return true;
    });

    when(event1.releaseInventory(ZONE_ID_2, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone2.release(InventorySelectionDTO.standing(1));
        return true;
    });

    when(event2.releaseInventory(ZONE_ID_3, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone3.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_2));
    assertEquals(0, activeOrder.countTickets(EVENT_ID_2, ZONE_ID_3));

    assertEquals(5, zone1.getAvailableAmount());
    assertEquals(5, zone2.getAvailableAmount());
    assertEquals(5, zone3.getAvailableAmount());

    assertEquals(0, zone1.getReservedAmount());
    assertEquals(0, zone2.getReservedAmount());
    assertEquals(0, zone3.getReservedAmount());
}
   @Test
void GivenPaymentRefundFails_WhenCheckout_ThenClearCartReturnTicketsToStockAndThrowRefundException() {
    InventoryZone zone = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    zone.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(
            EVENT_ID_1,
            ZONE_ID_1,
            1,
            100.0,
            LocalDateTime.now()
    );

    PaymentResultDTO paymentResult = new PaymentResultDTO(
            PAYMENT_TRANSACTION_ID,
            "gateway",
            100.0,
            "ILS",
            LocalDateTime.now()
    );

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone);

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(null);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone.release(InventorySelectionDTO.standing(1));
        return true;
    });

    doThrow(new RuntimeException("refund failed"))
            .when(mockPaymentGateway)
            .refund(PAYMENT_TRANSACTION_ID, 100.0);

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(5, zone.getAvailableAmount());
    assertEquals(0, zone.getReservedAmount());
}
    @Test
void GivenTicketFromDifferentEventDoesNotExist_WhenCheckout_ThenClearCartAndReturnTicketsToStock() {
    InventoryZone zone1 = new StandingZone(ZONE_ID_1, "VIP", 5, 100.0);
    InventoryZone zone3 = new StandingZone(ZONE_ID_3, "BALCONY", 5, 200.0);

    zone1.reserve(InventorySelectionDTO.standing(1));
    zone3.reserve(InventorySelectionDTO.standing(1));

    ActiveOrder activeOrder = new ActiveOrder(USER_ID);
    activeOrder.addStandingReservation(EVENT_ID_1, ZONE_ID_1, 1, 100.0, LocalDateTime.now());
    activeOrder.addStandingReservation(EVENT_ID_2, ZONE_ID_3, 1, 200.0, LocalDateTime.now());

    when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(event1);
    when(mockEventRepo.findById(EVENT_ID_2)).thenReturn(null);

    when(event1.getVenueMap().getZone(ZONE_ID_1)).thenReturn(zone1);

    when(event1.releaseInventory(ZONE_ID_1, InventorySelectionDTO.standing(1))).thenAnswer(invocation -> {
        zone1.release(InventorySelectionDTO.standing(1));
        return true;
    });

    assertThrows(RuntimeException.class, () ->
            checkoutService.checkout(
                    VALID_TOKEN,
                    IDEMPOTENCY_KEY,
                    CURRENCY,
                    PAYMENT_METHOD_TOKEN
            )
    );

    assertEquals(0, activeOrder.countTickets(EVENT_ID_1, ZONE_ID_1));
    assertEquals(0, activeOrder.countTickets(EVENT_ID_2, ZONE_ID_3));

    assertEquals(5, zone1.getAvailableAmount());
    assertEquals(0, zone1.getReservedAmount());
}
   @Test
void GivenValidCheckout_WhenCheckout_ThenReturnCheckoutResultAndSaveTicketAndReceipt() {
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
    when(mockOrder.buy()).thenReturn(List.of(itemEvent1Zone1));

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(100.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(issuanceResult);

    CheckoutResultDTO result = checkoutService.checkout(
            VALID_TOKEN,
            IDEMPOTENCY_KEY,
            CURRENCY,
            PAYMENT_METHOD_TOKEN
    );

    assertEquals(
            new CheckoutResultDTO(
                    100.0,
                    PAYMENT_TRANSACTION_ID,
                    List.of(TICKET_ID_1)
            ),
            result
    );

    ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
    verify(mockTicketRepo, times(1)).save(ticketCaptor.capture());

    Ticket savedTicket = ticketCaptor.getValue();
    assertEquals(TICKET_ID_1, savedTicket.getId());
    assertEquals(EVENT_ID_1, savedTicket.getEventId());
    assertEquals(ZONE_ID_1, savedTicket.getZoneId());
   

    ArgumentCaptor<OrderReceipt> receiptCaptor = ArgumentCaptor.forClass(OrderReceipt.class);
    verify(mockOrderReceiptRepo, times(1)).save(receiptCaptor.capture());

    OrderReceipt savedReceipt = receiptCaptor.getValue();
    assertEquals(USER_ID, savedReceipt.getUserid());
    assertEquals(100.0, savedReceipt.getTotalAmount());

}

   @Test
void GivenValidCheckoutWithCalculatedPrice_WhenCheckout_ThenReturnCalculatedTotalAndSaveTicketAndReceipt() {
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
    when(mockOrder.buy()).thenReturn(List.of(itemEvent1Zone2));

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(150.0);

    when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(150.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(issuanceResult);

    CheckoutResultDTO result = checkoutService.checkout(
            VALID_TOKEN,
            IDEMPOTENCY_KEY,
            CURRENCY,
            PAYMENT_METHOD_TOKEN
    );

    assertEquals(150.0, result.totalCharged());

    ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
    verify(mockTicketRepo, times(1)).save(ticketCaptor.capture());

    Ticket savedTicket = ticketCaptor.getValue();

    assertEquals(TICKET_ID_2, savedTicket.getId());
    assertEquals(EVENT_ID_1, savedTicket.getEventId());
    assertEquals(ZONE_ID_2, savedTicket.getZoneId());


    ArgumentCaptor<OrderReceipt> receiptCaptor = ArgumentCaptor.forClass(OrderReceipt.class);
    verify(mockOrderReceiptRepo, times(1)).save(receiptCaptor.capture());

    OrderReceipt savedReceipt = receiptCaptor.getValue();

    assertEquals(USER_ID, savedReceipt.getUserid());
    assertEquals(150.0, savedReceipt.getTotalAmount());
    
}
   @Test
void GivenMultipleTicketsFromDifferentZonesSameEvent_WhenCheckout_ThenBuyAllTicketsAndSaveTicketsAndReceipt() {
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
    when(mockOrder.buy()).thenReturn(List.of(itemEvent1Zone1, itemEvent1Zone2));

    when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(250.0);

    when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
            .thenReturn(125.0);

    when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
            .thenReturn(paymentResult);

    when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
            .thenReturn(issuanceResult);

    CheckoutResultDTO result = checkoutService.checkout(
            VALID_TOKEN,
            IDEMPOTENCY_KEY,
            CURRENCY,
            PAYMENT_METHOD_TOKEN
    );

    assertEquals(
            new CheckoutResultDTO(
                    250.0,
                    PAYMENT_TRANSACTION_ID,
                    List.of(TICKET_ID_1, TICKET_ID_2)
            ),
            result
    );

    ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
    verify(mockTicketRepo, times(2)).save(ticketCaptor.capture());

    List<Ticket> savedTickets = ticketCaptor.getAllValues();

    assertEquals(2, savedTickets.size());

    assertEquals(TICKET_ID_1, savedTickets.get(0).getId());
    assertEquals(EVENT_ID_1, savedTickets.get(0).getEventId());
    assertEquals(ZONE_ID_1, savedTickets.get(0).getZoneId());


    assertEquals(TICKET_ID_2, savedTickets.get(1).getId());
    assertEquals(EVENT_ID_1, savedTickets.get(1).getEventId());
    assertEquals(ZONE_ID_2, savedTickets.get(1).getZoneId());
    

    ArgumentCaptor<OrderReceipt> receiptCaptor = ArgumentCaptor.forClass(OrderReceipt.class);
    verify(mockOrderReceiptRepo, times(1)).save(receiptCaptor.capture());

    OrderReceipt savedReceipt = receiptCaptor.getValue();

    assertEquals(USER_ID, savedReceipt.getUserid());
    assertEquals(250.0, savedReceipt.getTotalAmount());
    
}
   @Test
   void GivenTicketsFromDifferentEvents_WhenCheckout_ThenBuyAllTicketsAndSaveTicketsAndReceipt() {
           PaymentResultDTO paymentResult = new PaymentResultDTO(
                           PAYMENT_TRANSACTION_ID,
                           "gateway",
                           300.0,
                           "ILS",
                           LocalDateTime.now());

           IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                           ISSUANCE_TRANSACTION_ID,
                           "issuer",
                           LocalDateTime.now(),
                           List.of(
                                           new BarcodeDTO(TICKET_ID_1, "barcode-event-1", "QR"),
                                           new BarcodeDTO(TICKET_ID_3, "barcode-event-2", "QR")));

           when(mockOrder.validateCanCheckout()).thenReturn(true);
           when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1, itemEvent2Zone3));
           when(mockOrder.buy()).thenReturn(List.of(itemEvent1Zone1, itemEvent2Zone3));

           when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
                           .thenReturn(100.0);

           when(event2.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
                           .thenReturn(200.0);

           when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
                           .thenReturn(100.0);

           when(event2.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
                           .thenReturn(200.0);

           when(mockPaymentGateway.charge(any(PaymentRequestDTO.class)))
                           .thenReturn(paymentResult);

           when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class)))
                           .thenReturn(issuanceResult);

           CheckoutResultDTO result = checkoutService.checkout(
                           VALID_TOKEN,
                           IDEMPOTENCY_KEY,
                           CURRENCY,
                           PAYMENT_METHOD_TOKEN);

           assertEquals(
                           new CheckoutResultDTO(
                                           300.0,
                                           PAYMENT_TRANSACTION_ID,
                                           List.of(TICKET_ID_1, TICKET_ID_3)),
                           result);

           ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
           verify(mockTicketRepo, times(2)).save(ticketCaptor.capture());

           List<Ticket> savedTickets = ticketCaptor.getAllValues();

           assertEquals(2, savedTickets.size());

           assertEquals(TICKET_ID_1, savedTickets.get(0).getId());
           assertEquals(EVENT_ID_1, savedTickets.get(0).getEventId());
           assertEquals(ZONE_ID_1, savedTickets.get(0).getZoneId());

           assertEquals(TICKET_ID_3, savedTickets.get(1).getId());
           assertEquals(EVENT_ID_2, savedTickets.get(1).getEventId());
           assertEquals(ZONE_ID_3, savedTickets.get(1).getZoneId());

           ArgumentCaptor<OrderReceipt> receiptCaptor = ArgumentCaptor.forClass(OrderReceipt.class);
           verify(mockOrderReceiptRepo, times(1)).save(receiptCaptor.capture());

           OrderReceipt savedReceipt = receiptCaptor.getValue();
           assertEquals(USER_ID, savedReceipt.getUserid());
           assertEquals(300.0, savedReceipt.getTotalAmount());

   }











        @Test
        void GivenSeatedCartLine_WhenCheckout_ThenIssuerReceivesSeatNumberAndTicketSavedWithSeatNumber() {
                CartLineItem seatedItem = mock(CartLineItem.class);

                when(seatedItem.geteventId()).thenReturn(EVENT_ID_1);
                when(seatedItem.getzoneId()).thenReturn(ZONE_ID_1);
                when(seatedItem.getSeatNumber()).thenReturn("A1");
                when(seatedItem.getPriceAtReservation()).thenReturn(120.0);

                PaymentResultDTO paymentResult = new PaymentResultDTO(
                                PAYMENT_TRANSACTION_ID,
                                "gateway",
                                120.0,
                                "ILS",
                                LocalDateTime.now());

                IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                                ISSUANCE_TRANSACTION_ID,
                                "issuer",
                                LocalDateTime.now(),
                                List.of(new BarcodeDTO(TICKET_ID_1, "barcode-A1", "QR")));

                when(mockOrder.validateCanCheckout()).thenReturn(true);
                when(mockOrder.getItems()).thenReturn(List.of(seatedItem));
                when(mockOrder.buy()).thenReturn(List.of(seatedItem));

                when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
                                .thenReturn(120.0);

                when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
                                .thenReturn(120.0);

                when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
                when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN);

                ArgumentCaptor<IssuanceRequestDTO> issuanceCaptor = ArgumentCaptor.forClass(IssuanceRequestDTO.class);
                verify(mockTicketIssuer).issue(issuanceCaptor.capture());

                IssuanceRequestDTO issuanceRequest = issuanceCaptor.getValue();

                assertEquals(1, issuanceRequest.items().size());
                assertEquals("A1", issuanceRequest.items().get(0).seatNumber());

                ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
                verify(mockTicketRepo).save(ticketCaptor.capture());

                Ticket savedTicket = ticketCaptor.getValue();

                assertEquals(EVENT_ID_1, savedTicket.getEventId());
                assertEquals(ZONE_ID_1, savedTicket.getZoneId());
                assertEquals("A1", savedTicket.getSeatNumber());
                assertEquals(TICKET_ID_1, savedTicket.getId());
        }

        

        @Test
        void GivenStandingCartLine_WhenCheckout_ThenIssuerReceivesNullSeatNumber() {
                PaymentResultDTO paymentResult = new PaymentResultDTO(
                                PAYMENT_TRANSACTION_ID,
                                "gateway",
                                100.0,
                                "ILS",
                                LocalDateTime.now());

                IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                                ISSUANCE_TRANSACTION_ID,
                                "issuer",
                                LocalDateTime.now(),
                                List.of(new BarcodeDTO(TICKET_ID_1, "barcode-standing", "QR")));

                when(itemEvent1Zone1.getSeatNumber()).thenReturn(null);

                when(mockOrder.validateCanCheckout()).thenReturn(true);
                when(mockOrder.getItems()).thenReturn(List.of(itemEvent1Zone1));
                when(mockOrder.buy()).thenReturn(List.of(itemEvent1Zone1));

                when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
                                .thenReturn(100.0);

                when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
                                .thenReturn(100.0);

                when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
                when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN);

                ArgumentCaptor<IssuanceRequestDTO> issuanceCaptor = ArgumentCaptor.forClass(IssuanceRequestDTO.class);
                verify(mockTicketIssuer).issue(issuanceCaptor.capture());

                assertNull(issuanceCaptor.getValue().items().get(0).seatNumber());
        }

        
        @Test
        void GivenSuccessfulSeatedCheckout_WhenCheckout_ThenReservedSeatBecomesSold() {
                SeatedZone seatedZone = new SeatedZone(
                                ZONE_ID_1,
                                "Orchestra",
                                120.0,
                                List.of(new Seat("A1", 0, 0)));

                seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1")));

                Event realEvent = createRealEventWithZone(EVENT_ID_1, seatedZone);

                ActiveOrder activeOrder = new ActiveOrder(USER_ID);
                activeOrder.addSeatedReservation(
                                EVENT_ID_1,
                                ZONE_ID_1,
                                List.of("A1"),
                                120.0,
                                LocalDateTime.now());

                PaymentResultDTO paymentResult = new PaymentResultDTO(
                                PAYMENT_TRANSACTION_ID,
                                "gateway",
                                120.0,
                                "ILS",
                                LocalDateTime.now());

                IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                                ISSUANCE_TRANSACTION_ID,
                                "issuer",
                                LocalDateTime.now(),
                                List.of(new BarcodeDTO(TICKET_ID_1, "barcode-A1", "QR")));

                when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);
                when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(realEvent);
                when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
                when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN);

                assertEquals(SeatStatus.SOLD, seatedZone.getSeat("A1").getStatus());
                assertTrue(activeOrder.isEmpty());
        }

        
        @Test
        void GivenTicketIssuanceFailsForSeatedOrder_WhenCheckout_ThenReservedSeatIsReleased() {
                SeatedZone seatedZone = new SeatedZone(
                                ZONE_ID_1,
                                "Orchestra",
                                120.0,
                                List.of(new Seat("A1", 0, 0)));

                seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1")));

                Event realEvent = createRealEventWithZone(EVENT_ID_1, seatedZone);

                ActiveOrder activeOrder = new ActiveOrder(USER_ID);
                activeOrder.addSeatedReservation(
                                EVENT_ID_1,
                                ZONE_ID_1,
                                List.of("A1"),
                                120.0,
                                LocalDateTime.now());

                PaymentResultDTO paymentResult = new PaymentResultDTO(
                                PAYMENT_TRANSACTION_ID,
                                "gateway",
                                120.0,
                                "ILS",
                                LocalDateTime.now());

                when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);
                when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(realEvent);
                when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
                when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(null);

                assertThrows(RuntimeException.class, () -> checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY,
                                CURRENCY, PAYMENT_METHOD_TOKEN));

                assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
                assertTrue(activeOrder.isEmpty());
        }
        
        

        @Test
        void GivenMixedStandingAndSeatedOrder_WhenCheckout_ThenAllTicketsSavedWithCorrectSeatNumbers() {
                CartLineItem standingItem = mock(CartLineItem.class);
                CartLineItem seatedItem = mock(CartLineItem.class);

                when(standingItem.geteventId()).thenReturn(EVENT_ID_1);
                when(standingItem.getzoneId()).thenReturn(ZONE_ID_1);
                when(standingItem.getSeatNumber()).thenReturn(null);
                when(standingItem.getPriceAtReservation()).thenReturn(50.0);

                when(seatedItem.geteventId()).thenReturn(EVENT_ID_1);
                when(seatedItem.getzoneId()).thenReturn(ZONE_ID_2);
                when(seatedItem.getSeatNumber()).thenReturn("B7");
                when(seatedItem.getPriceAtReservation()).thenReturn(120.0);

                PaymentResultDTO paymentResult = new PaymentResultDTO(
                        PAYMENT_TRANSACTION_ID,
                        "gateway",
                        170.0,
                        "ILS",
                        LocalDateTime.now()
                );

                IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                        ISSUANCE_TRANSACTION_ID,
                        "issuer",
                        LocalDateTime.now(),
                        List.of(
                                new BarcodeDTO(TICKET_ID_1, "barcode-standing", "QR"),
                                new BarcodeDTO(TICKET_ID_2, "barcode-B7", "QR")
                        )
                );

                when(mockOrder.validateCanCheckout()).thenReturn(true);
                when(mockOrder.getItems()).thenReturn(List.of(standingItem, seatedItem));
                when(mockOrder.buy()).thenReturn(List.of(standingItem, seatedItem));

                when(event1.calculatePrice(anyInt(), anyDouble(), any(LocalDateTime.class)))
                        .thenAnswer(invocation -> {
                                int quantity = invocation.getArgument(0);
                                double price = invocation.getArgument(1);
                                return quantity * price;
                        });

                when(event1.calculatePriceforoneticket(anyInt(), anyDouble(), any(LocalDateTime.class)))
                        .thenAnswer(invocation -> invocation.getArgument(1));

                when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
                when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

                checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN);

                ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
                verify(mockTicketRepo, times(2)).save(ticketCaptor.capture());

                List<Ticket> savedTickets = ticketCaptor.getAllValues();

                assertNull(savedTickets.get(0).getSeatNumber());
                assertEquals("B7", savedTickets.get(1).getSeatNumber());
        }



        // new test
        @Test
        void GivenReceiptSaveFailsAfterSeatConfirmation_WhenCheckout_ThenSystemDoesNotLoseSeatOrCart() {
                // Arrange seated seat A1 RESERVED in cart
                // Make payment and issuance succeed
                // Make orderReceiptRepository.save(...) throw
                // Assert checkout throws
                // Assert seat is not left in an impossible state
                // Assert active order is not silently lost
                SeatedZone seatedZone = new SeatedZone(
                                ZONE_ID_1,
                                "Orchestra",
                                120.0,
                                List.of(new Seat("A1", 0, 0)));
                seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1")));
                Event realEvent = createRealEventWithZone(EVENT_ID_1, seatedZone);
                ActiveOrder activeOrder = new ActiveOrder(USER_ID);

                activeOrder.addSeatedReservation(
                                EVENT_ID_1,
                                ZONE_ID_1,
                                List.of("A1"),
                                120.0,
                                LocalDateTime.now());

                PaymentResultDTO paymentResult = new PaymentResultDTO(
                                PAYMENT_TRANSACTION_ID,
                                "gateway",
                                120.0,
                                                "ILS",
                                LocalDateTime.now());

                IssuanceResultDTO issuanceResult = new IssuanceResultDTO(
                                ISSUANCE_TRANSACTION_ID,
                                "issuer",
                                LocalDateTime.now(),
                                List.of(new BarcodeDTO(TICKET_ID_1, "barcode-A1", "QR")));
                                
                when(mockActiveOrderRepo.getByUserId(USER_ID)).thenReturn(activeOrder);
                when(mockEventRepo.findById(EVENT_ID_1)).thenReturn(realEvent);
                when(mockPaymentGateway.charge(any(PaymentRequestDTO.class))).thenReturn(paymentResult);
                when(mockTicketIssuer.issue(any(IssuanceRequestDTO.class))).thenReturn(issuanceResult);

                doThrow(new RuntimeException("DB error on receipt save"))
                                .when(mockOrderReceiptRepo).save(any(OrderReceipt.class));

                assertThrows(RuntimeException.class, () -> checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY,
                                CURRENCY, PAYMENT_METHOD_TOKEN));
                                
                assertEquals(SeatStatus.SOLD, seatedZone.getSeat("A1").getStatus());
                assertFalse(activeOrder.isEmpty());
                assertThrows(RuntimeException.class, () -> checkoutService.checkout(VALID_TOKEN, IDEMPOTENCY_KEY, CURRENCY, PAYMENT_METHOD_TOKEN));
        }












   // test helper functions:

   private PurchasePolicy acceptingPurchasePolicy() {
           return new PurchasePolicy(10);
        }

        private DiscountPolicy noDiscountPolicy() {
        return new DiscountPolicy(0) {
                @Override
                public double calculate(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
                return quantity * priceAtOneTicketReservation;
                }

                @Override
                public double calculatePriceforoneticket(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
                return priceAtOneTicketReservation;
                }
        };
        }

        private Event createRealEventWithZone(int eventId, InventoryZone zone) {
        return new Event(
                eventId,
                "Concert",
                4.5,
                List.of("Artist"),
                EventCategory.CONCERT,
                100,
                EventStatus.SCHEDULED,
                new VenueMap(1, new Location("Israel", "Tel Aviv"), List.of(zone)),
                List.of(),
                acceptingPurchasePolicy(),
                noDiscountPolicy()
        );
        }





}