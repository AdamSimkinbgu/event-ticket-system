package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.support.BaseDomainTest;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReceiptLineTest extends BaseDomainTest {

    @Test
    void testGetTicketIdReturnsCorrectId() {
        ReceiptLine line = track(new ReceiptLine(100, 50.0, 10, LocalDateTime.now()));
        assertEquals(100, line.getTicketId());
    }

    @Test
    void testGetEventIdReturnsCorrectEventId() {
        ReceiptLine line = track(new ReceiptLine(100, 50.0, 10, LocalDateTime.now()));
        assertEquals(10, line.getEventId());
    }

    @Test
    void testGetPriceAtReservationReturnsCorrectPrice() {
        ReceiptLine line = track(new ReceiptLine(100, 50.0, 10, LocalDateTime.now()));
        assertEquals(50.0, line.getPriceAtReservation());
    }
}