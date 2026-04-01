package com.bgu.se.ticketing.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Order} domain aggregate root.
 */
class OrderTest {

    @Test
    @DisplayName("Order.create – produces a PENDING order with correct buyerId and eventId")
    void create_producesCorrectInitialState() {
        Order order = Order.create("buyer-1", "event-1");

        assertThat(order.getId()).isNotBlank();
        assertThat(order.getBuyerId()).isEqualTo("buyer-1");
        assertThat(order.getEventId()).isEqualTo("event-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTickets()).isEmpty();
    }

    @Test
    @DisplayName("addTicket – attaches ticket to PENDING order")
    void addTicket_pendingOrder_ticketIsAdded() {
        Order order = Order.create("buyer-1", "event-1");
        Ticket ticket = Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50));

        order.addTicket(ticket);

        assertThat(order.getTickets()).hasSize(1);
        assertThat(order.getTickets().get(0)).isEqualTo(ticket);
    }

    @Test
    @DisplayName("addTicket – throws when order is not PENDING")
    void addTicket_confirmedOrder_throwsIllegalState() {
        Order order = Order.create("buyer-1", "event-1");
        order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50)));
        order.confirm();

        assertThatThrownBy(() -> order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("confirm – transitions status to CONFIRMED")
    void confirm_pendingOrderWithTickets_statusBecomesConfirmed() {
        Order order = Order.create("buyer-1", "event-1");
        order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50)));

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirm – throws when order has no tickets")
    void confirm_emptyOrder_throwsIllegalState() {
        Order order = Order.create("buyer-1", "event-1");

        assertThatThrownBy(order::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cancel – transitions status to CANCELLED")
    void cancel_pendingOrder_statusBecomesCancelled() {
        Order order = Order.create("buyer-1", "event-1");
        order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50)));

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel – throws when already cancelled")
    void cancel_alreadyCancelledOrder_throwsIllegalState() {
        Order order = Order.create("buyer-1", "event-1");
        order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50)));
        order.cancel();

        assertThatThrownBy(order::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("calculateTotal – sums all ticket prices")
    void calculateTotal_multipleTickets_returnsCorrectSum() {
        Order order = Order.create("buyer-1", "event-1");
        order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(50)));
        order.addTicket(Ticket.create("event-1", "buyer-1", BigDecimal.valueOf(75)));

        assertThat(order.calculateTotal()).isEqualByComparingTo(BigDecimal.valueOf(125));
    }
}
