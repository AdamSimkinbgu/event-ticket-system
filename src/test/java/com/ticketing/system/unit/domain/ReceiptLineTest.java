package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.orders.ReceiptLine;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptLineTest {

    @Test
    void testGetTicketIdReturnsCorrectId() {
        ReceiptLine line = new ReceiptLine(100, 50.0, 10, LocalDateTime.now());
        assertEquals(100, line.getTicketId());
    }

    @Test
    void testGetEventIdReturnsCorrectEventId() {
        ReceiptLine line = new ReceiptLine(100, 50.0, 10, LocalDateTime.now());
        assertEquals(10, line.getEventId());
    }

    @Test
    void testGetPriceAtReservationReturnsCorrectPrice() {
        ReceiptLine line = new ReceiptLine(100, 50.0, 10, LocalDateTime.now());
        assertEquals(50.0, line.getPriceAtReservation());
    }
}