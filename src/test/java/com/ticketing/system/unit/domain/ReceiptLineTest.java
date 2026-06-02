package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.support.BaseDomainTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

class ReceiptLineTest extends BaseDomainTest {


    @Test
    void testGetTicketIdReturnsCorrectId() {
        ReceiptLine line = track(new ReceiptLine(100, 50.0, 10, 1, null, LocalDateTime.now()));
        assertEquals(100, line.getTicketId());
    }

    @Test
    void testGetEventIdReturnsCorrectEventId() {
        ReceiptLine line = track(new ReceiptLine(100, 50.0, 10, 1, null, LocalDateTime.now()));
        assertEquals(10, line.getEventId());
    }

    @Test
    void testGetPriceAtReservationReturnsCorrectPrice() {
        ReceiptLine line = track(new ReceiptLine(100, 50.0, 10, 1, null, LocalDateTime.now()));
        assertEquals(50.0, line.getPriceAtReservation());
    }






    @Test
    void GivenStandingReceiptLine_WhenCreated_ThenSeatNumberIsNull() {
        ReceiptLine line = track(new ReceiptLine(
                100,
                50.0,
                10,
                5,
                null,
                LocalDateTime.now()
        ));

        assertEquals(100, line.getTicketId());
        assertEquals(10, line.getEventId());
        assertEquals(5, line.getZoneId());
        assertNull(line.getSeatNumber());
    }

    @Test
    void GivenSeatedReceiptLine_WhenCreated_ThenSeatNumberIsPreserved() {
        ReceiptLine line = track(new ReceiptLine(
                101,
                120.0,
                10,
                7,
                "B14",
                LocalDateTime.now()
        ));

        assertEquals(101, line.getTicketId());
        assertEquals(10, line.getEventId());
        assertEquals(7, line.getZoneId());
        assertEquals("B14", line.getSeatNumber());
    }

    @Test
    void GivenBlankSeatNumber_WhenCheckInvariants_ThenThrowsException() {
        ReceiptLine line = new ReceiptLine(
                101,
                120.0,
                10,
                7,
                "   ",
                LocalDateTime.now()
        );

        assertThrows(IllegalStateException.class, line::checkInvariants);
    }






}