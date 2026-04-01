package com.bgu.se.ticketing.application.services;

import com.bgu.se.ticketing.application.dto.CreateOrderRequestDTO;
import com.bgu.se.ticketing.application.dto.OrderDTO;
import com.bgu.se.ticketing.domain.models.*;
import com.bgu.se.ticketing.domain.repositories.IOrderRepository;
import com.bgu.se.ticketing.domain.services.TicketReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>Uses JUnit 5 and Mockito. All dependencies are mocked so no Spring context
 * is started, keeping tests fast and focused.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private IOrderRepository orderRepository;

    @Mock
    private TicketReservationService ticketReservationService;

    @InjectMocks
    private OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.create("buyer-1", "event-1");
        Ticket ticket = Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50));
        sampleOrder.addTicket(ticket);
    }

    // -------------------------------------------------------------------------
    // createOrder
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createOrder – happy path persists and returns DTO")
    void createOrder_happyPath_returnsSavedOrderDTO() {
        CreateOrderRequestDTO request = new CreateOrderRequestDTO();
        request.setBuyerId("buyer-1");
        request.setEventId("event-1");
        request.setQuantity(1);

        when(orderRepository.save(any(Order.class))).thenReturn(sampleOrder);

        OrderDTO result = orderService.createOrder(request);

        assertThat(result).isNotNull();
        assertThat(result.getBuyerId()).isEqualTo("buyer-1");
        assertThat(result.getEventId()).isEqualTo("event-1");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(ticketReservationService).reserveTickets(any(Order.class), eq(1));
        verify(orderRepository).save(any(Order.class));
    }

    // -------------------------------------------------------------------------
    // confirmOrder
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("confirmOrder – confirms a PENDING order")
    void confirmOrder_pendingOrder_returnsConfirmedDTO() {
        String orderId = sampleOrder.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDTO result = orderService.confirmOrder(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("confirmOrder – throws when order not found")
    void confirmOrder_orderNotFound_throwsNoSuchElement() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder("missing"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing");
    }

    // -------------------------------------------------------------------------
    // cancelOrder
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cancelOrder – cancels a PENDING order and releases tickets")
    void cancelOrder_pendingOrder_returnsCancelledDTO() {
        String orderId = sampleOrder.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDTO result = orderService.cancelOrder(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(ticketReservationService).releaseTickets(sampleOrder);
        verify(orderRepository).save(sampleOrder);
    }

    @Test
    @DisplayName("cancelOrder – throws when order not found")
    void cancelOrder_orderNotFound_throwsNoSuchElement() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // -------------------------------------------------------------------------
    // getOrder
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getOrder – returns DTO for existing order")
    void getOrder_existingId_returnsDTO() {
        when(orderRepository.findById(sampleOrder.getId()))
                .thenReturn(Optional.of(sampleOrder));

        OrderDTO result = orderService.getOrder(sampleOrder.getId());

        assertThat(result.getId()).isEqualTo(sampleOrder.getId());
    }

    @Test
    @DisplayName("getOrder – throws when order not found")
    void getOrder_missingId_throwsNoSuchElement() {
        when(orderRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("bad-id"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // -------------------------------------------------------------------------
    // getOrdersByBuyer
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getOrdersByBuyer – returns list of orders for buyer")
    void getOrdersByBuyer_returnsCorrectList() {
        when(orderRepository.findByBuyerId("buyer-1")).thenReturn(List.of(sampleOrder));

        List<OrderDTO> results = orderService.getOrdersByBuyer("buyer-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getBuyerId()).isEqualTo("buyer-1");
    }
}
