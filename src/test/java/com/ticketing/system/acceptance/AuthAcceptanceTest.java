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
import com.ticketing.system.Core.Application.dto.GuestSessionDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.LogoutRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.GuestSessionRequiredException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Session;
import com.ticketing.system.Core.Domain.users.User;

@SpringBootTest
@ActiveProfiles("test")
class AuthAcceptanceTest {

    @Autowired private AuthenticationService authService;
    @Autowired private IUserRepository userRepository;
    @Autowired private ISessionRepository sessionRepository;

    // ------------------------------------------------------------------
    // Guest session lifecycle
    // ------------------------------------------------------------------

    @Test
    void GuestSession_StartsAndGetsRowPersisted() {
        GuestSessionDTO dto = authService.startGuestSession();

        assertNotNull(dto.sessionId());
        assertFalse(dto.sessionId().isBlank());
        Session row = sessionRepository.findById(dto.sessionId()).orElseThrow();
        assertTrue(row.isGuest());
    }

    @Test
    void GuestSession_EndDeletesRow() {
        GuestSessionDTO dto = authService.startGuestSession();
        assertTrue(sessionRepository.findById(dto.sessionId()).isPresent());

        authService.endGuestSession(dto.sessionId());

        assertFalse(sessionRepository.findById(dto.sessionId()).isPresent());
    }

    // ------------------------------------------------------------------
    // UC-11 — Register requires Guest session (D10a)
    // ------------------------------------------------------------------

    @Test
    void GivenUniqueDetails_WhenRegister_ThenMemberCreated() {
        String sid = authService.startGuestSession().sessionId();

        authService.register(new RegisterRequestDTO("alice", "alice@example.com", "Password1", sid));

        assertTrue(userRepository.existsByUsername("alice"));
        User stored = userRepository.findByUsername("alice").orElseThrow();
        assertEquals("alice", stored.getUsername());
        assertEquals("alice@example.com", stored.getEmail());
    }

    @Test
    void GivenSuccessfulRegistration_WhenCheckSession_ThenStillGuest() {
        String sid = authService.startGuestSession().sessionId();

        authService.register(new RegisterRequestDTO("carol", "carol@example.com", "Password1", sid));

        // II.1.4 / D10a: session must still be Guest after register.
        Session row = sessionRepository.findById(sid).orElseThrow();
        assertTrue(row.isGuest());
        // User was actually created.
        assertTrue(userRepository.existsByUsername("carol"));
    }

    @Test
    void GivenDuplicateUsername_WhenRegister_ThenRejected() {
        String sid1 = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("bob", "bob@example.com", "Password1", sid1));

        String sid2 = authService.startGuestSession().sessionId();
        assertThrows(DuplicateUsernameException.class, () ->
            authService.register(new RegisterRequestDTO("bob", "bob2@example.com", "Password1", sid2))
        );
    }

    @Test
    void GivenNoGuestSession_WhenRegister_ThenGuestSessionRequired() {
        assertThrows(GuestSessionRequiredException.class, () ->
            authService.register(new RegisterRequestDTO("nobody", "nobody@example.com", "Password1", null))
        );
        assertFalse(userRepository.existsByUsername("nobody"));
    }

    // ------------------------------------------------------------------
    // UC-12 — Login promotes Guest session in place
    // ------------------------------------------------------------------

    @Test
    void GivenValidCredentials_WhenLogin_ThenTokenIssued() {
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("dave", "dave@example.com", "Password1", sid));

        AuthTokenDTO result = authService.login(new LoginRequestDTO("dave", "Password1", sid));

        assertNotNull(result.token());
        assertFalse(result.token().isBlank());
        assertEquals("dave", result.username());
        assertTrue(authService.validateToken(result.token()));
        assertEquals(result.userId(), authService.extractUserId(result.token()));
        assertTrue(result.expiresAtEpochMillis() > System.currentTimeMillis());
    }

    @Test
    void GivenLogin_ThenGuestSessionPromotedInPlace_SidPreserved() {
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("hank", "hank@example.com", "Password1", sid));

        AuthTokenDTO result = authService.login(new LoginRequestDTO("hank", "Password1", sid));

        // Same Session row — userId is now set, sessionId preserved.
        Session row = sessionRepository.findById(sid).orElseThrow();
        assertTrue(row.isMember());
        assertEquals(result.userId(), row.getUserId());
        assertEquals(sid, row.getSessionId());
    }

    @Test
    void GivenInvalidCredentials_WhenLogin_ThenGenericReject() {
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("eve", "eve@example.com", "Password1", sid));

        // Wrong password and unknown user yield the SAME exception — no enumeration leak.
        // Need a fresh guest session each time because the previous would be promoted on success.
        String sidA = authService.startGuestSession().sessionId();
        assertThrows(AuthenticationFailedException.class, () ->
            authService.login(new LoginRequestDTO("eve", "wrongpass", sidA))
        );
        String sidB = authService.startGuestSession().sessionId();
        assertThrows(AuthenticationFailedException.class, () ->
            authService.login(new LoginRequestDTO("nosuchuser", "anything1", sidB))
        );
    }

    @Test
    void GivenNoGuestSession_WhenLogin_ThenGuestSessionRequired() {
        assertThrows(GuestSessionRequiredException.class, () ->
            authService.login(new LoginRequestDTO("alice", "Password1", null))
        );
    }

    @Test
    void GivenLoginWithMemberSessionId_WhenLogin_ThenGuestSessionRequired() {
        // Promoting twice on the same sid should be rejected — second login must use a fresh Guest.
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("ivy", "ivy@example.com", "Password1", sid));
        authService.login(new LoginRequestDTO("ivy", "Password1", sid));   // first promotion succeeds

        assertThrows(GuestSessionRequiredException.class, () ->
            authService.login(new LoginRequestDTO("ivy", "Password1", sid))
        );
    }

    // UC-13
    @Test @Disabled("UC-13 main: pending order restored on login (Phase 4/5 work)")
    void GivenMemberWithPendingOrder_WhenLogin_ThenOrderRestored() {}
    @Test @Disabled("UC-13 alt: expired order is not restored")
    void GivenExpiredOrder_WhenLogin_ThenNoRestoration() {}

    // ------------------------------------------------------------------
    // UC-14 — Logout (D8 = L1: session row deleted)
    // ------------------------------------------------------------------

    @Test
    void GivenLoggedInMember_WhenLogout_ThenStateGuest() {
        // II.3.1 / D8: logout deletes the Session row entirely.
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("frank", "frank@example.com", "Password1", sid));
        AuthTokenDTO session = authService.login(new LoginRequestDTO("frank", "Password1", sid));
        assertTrue(authService.validateToken(session.token()));

        authService.logout(new LogoutRequestDTO(session.token()));

        assertThrows(InvalidTokenException.class, () -> authService.validateToken(session.token()));
        // Session row gone — user must startGuestSession() to act again (D8 = L1).
        assertFalse(sessionRepository.findById(sid).isPresent());
    }

    @Test
    void GivenAlreadyLoggedOutToken_WhenLogoutAgain_ThenNoError() {
        // Idempotency: repeating logout with the same (already-revoked) token must not throw.
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO("grace", "grace@example.com", "Password1", sid));
        AuthTokenDTO session = authService.login(new LoginRequestDTO("grace", "Password1", sid));
        authService.logout(new LogoutRequestDTO(session.token()));

        // Second call should silently succeed.
        authService.logout(new LogoutRequestDTO(session.token()));
    }

    @Test
    void DifferentGuestSessionsHaveDifferentIds() {
        String sidA = authService.startGuestSession().sessionId();
        String sidB = authService.startGuestSession().sessionId();
        assertNotEquals(sidA, sidB);
    }

    @Test @Disabled("UC-14 (II.3.0.1): cart-on-logout persistence — D9a, Phase 4/5 work")
    void GivenLoggedInWithOrder_WhenLogout_ThenOrderStaysLinked() {}
}
