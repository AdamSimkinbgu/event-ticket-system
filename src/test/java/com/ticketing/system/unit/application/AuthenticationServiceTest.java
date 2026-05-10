package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {
    @Test @Disabled("UC-11: register hashes password before reaching domain")
    void givenRegistrationData_whenRegister_thenUserCreatedWithHashedPassword() {}

    @Test @Disabled("UC-11: registration leaves session as Guest (II.1.4)")
    void givenSuccessfulRegistration_whenCheckSession_thenStillGuest() {}

    @Test @Disabled("UC-12: login on valid credentials issues JWT")
    void givenValidCredentials_whenLogin_thenTokenIssued() {}

    @Test @Disabled("UC-12: login on invalid credentials rejects with generic error")
    void givenInvalidCredentials_whenLogin_thenRejected() {}

    @Test @Disabled("UC-14: logout invalidates session, abandons cart (II.3.0.1)")
    void givenLoggedInMember_whenLogout_thenSessionEndedAndCartReleased() {}
}
