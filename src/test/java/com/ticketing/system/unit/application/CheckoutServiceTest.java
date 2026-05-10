package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {
    @Test @Disabled("UC-10: successful checkout — charge + issue + receipt + notify")
    void givenValidOrder_whenCheckout_thenChargedIssuedReceipted() {}

    @Test @Disabled("UC-10: II.2.8.2 atomic — payment fails → no tickets issued, locks released")
    void givenChargeFailure_whenCheckout_thenNothingPersisted() {}

    @Test @Disabled("UC-10 + UC-4: issuance fails → triggers refund pipeline")
    void givenIssuanceFailure_whenCheckout_thenRefundFires() {}

    @Test @Disabled("UC-10: II.2.8.1 expired timer rejects checkout")
    void givenExpiredOrder_whenCheckout_thenRejected() {}

    @Test @Disabled("UC-10: II.2.8.1 policy violation rejects checkout")
    void givenPolicyViolation_whenCheckout_thenRejected() {}

    @Test @Disabled("UC-33: payment gateway is called with correct amount")
    void givenSuccessfulCharge_whenCheckout_thenGatewayInvokedCorrectly() {}

    @Test @Disabled("UC-34: ticket issuer is called for issued tickets")
    void givenSuccessfulIssuance_whenCheckout_thenIssuerInvokedCorrectly() {}
}
