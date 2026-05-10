package com.ticketing.system.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Acceptance tests for the auth slice. Black-box, exercises application services only.
@SpringBootTest
@ActiveProfiles("test")
class AuthAcceptanceTest {

    // UC-11
    @Test @Disabled("UC-11 main: register member with valid unique details")
    void GivenUniqueDetails_WhenRegister_ThenMemberCreated() {}
    @Test @Disabled("UC-11 negative: duplicate username rejected")
    void GivenDuplicateUsername_WhenRegister_ThenRejected() {}
    @Test @Disabled("UC-11 alt: registration leaves session as Guest (II.1.4)")
    void GivenSuccessfulRegistration_WhenCheckSession_ThenStillGuest() {}

    // UC-12
    @Test @Disabled("UC-12 main: login with valid credentials issues JWT")
    void GivenValidCredentials_WhenLogin_ThenTokenIssued() {}
    @Test @Disabled("UC-12 negative: invalid credentials → generic rejection (no enumeration)")
    void GivenInvalidCredentials_WhenLogin_ThenGenericReject() {}

    // UC-13
    @Test @Disabled("UC-13 main: pending order restored on login")
    void GivenMemberWithPendingOrder_WhenLogin_ThenOrderRestored() {}
    @Test @Disabled("UC-13 alt: expired order is not restored")
    void GivenExpiredOrder_WhenLogin_ThenNoRestoration() {}

    // UC-14
    @Test @Disabled("UC-14 main: intentional logout abandons cart, releases tickets (II.3.0.1)")
    void GivenLoggedInWithCart_WhenLogout_ThenCartReleased() {}
    @Test @Disabled("UC-14 alt: session downgrades to Guest")
    void GivenLoggedInMember_WhenLogout_ThenStateGuest() {}
}
