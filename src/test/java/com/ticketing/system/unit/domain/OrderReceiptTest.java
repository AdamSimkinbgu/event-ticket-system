package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.support.BaseDomainTest;

import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderReceiptTest extends BaseDomainTest {

    private final int USER_ID = 5;
    private final int RECEIPT_ID = 5;

    @Test
    void testForMemberSetsUserIdCorrectly() {
        OrderReceipt receipt = track(OrderReceipt.forMember(RECEIPT_ID, USER_ID, 100.0, new ArrayList<>()));
        assertEquals(5, receipt.getUserid());
    }

    @Test
    void testForMemberIdentifiesAsMemberReceipt() {
        OrderReceipt receipt = track(OrderReceipt.forMember(RECEIPT_ID, USER_ID, 100.0, new ArrayList<>()));
        assertTrue(receipt.isMemberReceipt());
    }

    @Test
    void testForGuestSetsGuestEmailCorrectly() {
        OrderReceipt receipt = track(OrderReceipt.forGuest("test@test.com", "session-123", RECEIPT_ID, 100.0, new ArrayList<>()));
        assertEquals("test@test.com", receipt.getGuestEmail());
    }

    @Test
    void testForGuestIdentifiesAsGuestReceipt() {
        OrderReceipt receipt = track(OrderReceipt.forGuest("test@test.com", "session-123", RECEIPT_ID, 100.0, new ArrayList<>()));
        assertTrue(receipt.isGuestReceipt());
    }

    @Test
    void testMarkRefundedChangesRefundStateToTrue() {
        OrderReceipt receipt = track(OrderReceipt.forMember(RECEIPT_ID, USER_ID, 100.0, new ArrayList<>()));
        receipt.markRefunded();
        assertTrue(receipt.wasRefunded());
    }
}