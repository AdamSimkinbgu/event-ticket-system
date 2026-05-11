package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class MemberAccountServiceTest {
    @Test @Disabled("UC-16: viewMyHistory returns own purchase history")
    void givenAuthenticatedMember_whenViewMyHistory_thenOwnHistoryReturned() {}

    @Test @Disabled("UC-16: cannot view another member's history")
    void givenOtherUserId_whenViewMyHistory_thenRejected() {}

    @Test @Disabled("UC-16 + II.3.5.2: history reflects price-at-purchase, not current price")
    void givenPriceChangedAfterSale_whenViewMyHistory_thenOriginalPriceShown() {}
}
