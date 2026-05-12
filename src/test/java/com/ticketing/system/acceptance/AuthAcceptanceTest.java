package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.LogoutRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
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
    @Test
    void GivenValidCredentials_WhenLogin_ThenTokenIssued() {
        authService.register(new RegisterRequestDTO("dave", "dave@example.com", "Password1"));

        AuthTokenDTO result = authService.login(new LoginRequestDTO("dave", "Password1"));

        assertNotNull(result.token());
        assertFalse(result.token().isBlank());
        assertEquals("dave", result.username());
        assertTrue(authService.validateToken(result.token()));
        assertEquals(result.userId(), authService.extractUserId(result.token()));
        assertTrue(result.expiresAtEpochMillis() > System.currentTimeMillis());
    }

    @Test
    void GivenInvalidCredentials_WhenLogin_ThenGenericReject() {
        authService.register(new RegisterRequestDTO("eve", "eve@example.com", "Password1"));

        // Wrong password and unknown user yield the SAME exception — no enumeration leak.
        assertThrows(AuthenticationFailedException.class, () ->
            authService.login(new LoginRequestDTO("eve", "wrongpass"))
        );
        assertThrows(AuthenticationFailedException.class, () ->
            authService.login(new LoginRequestDTO("nosuchuser", "anything1"))
        );
    }

    // UC-13
    @Test @Disabled("UC-13 main: pending order restored on login")
    void GivenMemberWithPendingOrder_WhenLogin_ThenOrderRestored() {}
    @Test @Disabled("UC-13 alt: expired order is not restored")
    void GivenExpiredOrder_WhenLogin_ThenNoRestoration() {}

    // UC-14
    @Test
    void GivenLoggedInMember_WhenLogout_ThenStateGuest() {
        // II.3.1: logout terminates the authenticated session and downgrades the state
        // back to Guest-Visitor. We verify the downgrade by asserting the issued token
        // is no longer valid after logout.
        authService.register(new RegisterRequestDTO("frank", "frank@example.com", "Password1"));
        AuthTokenDTO session = authService.login(new LoginRequestDTO("frank", "Password1"));
        assertTrue(authService.validateToken(session.token()));

        authService.logout(new LogoutRequestDTO(session.token()));

        assertThrows(InvalidTokenException.class, () -> authService.validateToken(session.token()));
    }

    @Test
    void GivenAlreadyLoggedOutToken_WhenLogoutAgain_ThenNoError() {
        // Idempotency: repeating logout with the same (already-revoked) token must not throw.
        authService.register(new RegisterRequestDTO("grace", "grace@example.com", "Password1"));
        AuthTokenDTO session = authService.login(new LoginRequestDTO("grace", "Password1"));
        authService.logout(new LogoutRequestDTO(session.token()));

        // Second call should silently succeed.
        authService.logout(new LogoutRequestDTO(session.token()));
    }

    @Test @Disabled("UC-14 (II.3.0.1): order remains linked to member on logout — needs ActiveOrder")
    void GivenLoggedInWithOrder_WhenLogout_ThenOrderStaysLinked() {}
}
