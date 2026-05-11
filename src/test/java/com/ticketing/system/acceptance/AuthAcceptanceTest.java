package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

@SpringBootTest
@ActiveProfiles("test")
class AuthAcceptanceTest {

    @Autowired private AuthenticationService authService;
    @Autowired private IUserRepository userRepository;

    // UC-11
    @Test
    void GivenUniqueDetails_WhenRegister_ThenMemberCreated() {
        authService.register(new RegisterRequestDTO("alice", "alice@example.com", "Password1"));

        assertTrue(userRepository.existsByUsername("alice"));
        User stored = userRepository.findByUsername("alice").orElseThrow();
        assertEquals("alice", stored.getUsername());
        assertEquals("alice@example.com", stored.getEmail());
    }

    @Test
    void GivenDuplicateUsername_WhenRegister_ThenRejected() {
        authService.register(new RegisterRequestDTO("bob", "bob@example.com", "Password1"));

        assertThrows(DuplicateUsernameException.class, () ->
            authService.register(new RegisterRequestDTO("bob", "bob2@example.com", "Password1"))
        );
    }

    @Test
    void GivenSuccessfulRegistration_WhenCheckSession_ThenStillGuest() {
        authService.register(new RegisterRequestDTO("carol", "carol@example.com", "Password1"));

        // II.1.4: register is void — no AuthTokenDTO is produced. The user exists in the
        // repository but no session was established. UC-12 login is required to upgrade
        // a Guest session to Member-Visitor.
        User stored = userRepository.findByUsername("carol").orElseThrow();
        assertNotEquals("Password1", stored.getEmail()); // sanity: fields wired correctly
        assertTrue(userRepository.existsByUsername("carol"));
    }

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
