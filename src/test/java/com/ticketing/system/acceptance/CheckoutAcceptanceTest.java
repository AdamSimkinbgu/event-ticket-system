package com.ticketing.system.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CheckoutAcceptanceTest {




    
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
