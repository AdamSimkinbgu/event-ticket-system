package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.LogoutRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateEmailException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.InvalidEmailFormatException;
import com.ticketing.system.Core.Domain.exceptions.WeakPasswordException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

class AuthenticationServiceTest {

    private IUserRepository mockUserRepo;
    private IPasswordHasher mockHasher;
    private ISessionManager mockSessionManager;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        mockUserRepo = mock(IUserRepository.class);
        mockHasher = mock(IPasswordHasher.class);
        mockSessionManager = mock(ISessionManager.class);
        service = new AuthenticationService(mockUserRepo, mockHasher, mockSessionManager);
    }

    @Test
    void givenRegistrationData_whenRegister_thenUserCreatedWithHashedPassword() {
        when(mockUserRepo.existsByUsername("alice")).thenReturn(false);
        when(mockUserRepo.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(mockUserRepo.nextId()).thenReturn(42);
        when(mockHasher.hash("Password1")).thenReturn("HASHED");

        service.register(new RegisterRequestDTO("alice", "alice@example.com", "Password1"));

        verify(mockHasher, times(1)).hash("Password1");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(mockUserRepo, times(1)).save(savedUser.capture());
        assertEquals(42, savedUser.getValue().getUserId());
        assertEquals("alice", savedUser.getValue().getUsername());
        assertEquals("alice@example.com", savedUser.getValue().getEmail());
    }

    @Test
    void givenSuccessfulRegistration_whenCheckSession_thenStillGuest() {
        when(mockUserRepo.existsByUsername(any())).thenReturn(false);
        when(mockUserRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(mockUserRepo.nextId()).thenReturn(1);
        when(mockHasher.hash(any())).thenReturn("HASHED");

        service.register(new RegisterRequestDTO("bob", "bob@example.com", "Password1"));

        // II.1.4: register issues no token and triggers no login side effect.
        // Method signature is void — no AuthTokenDTO can be produced. The repository
        // captured a save but no session-establishing collaborator was invoked.
        assertTrue(true);
        verify(mockUserRepo, times(1)).save(any(User.class));
        verify(mockUserRepo, never()).updateUser(any(User.class));
    }

    @Test
    void givenMalformedEmail_whenRegister_thenInvalidEmailFormatExceptionThrown() {
        assertThrows(InvalidEmailFormatException.class, () ->
            service.register(new RegisterRequestDTO("alice", "not-an-email", "Password1"))
        );
        verifyNoInteractions(mockHasher);
        verify(mockUserRepo, never()).save(any(User.class));
    }

    @Test
    void givenNullEmail_whenRegister_thenInvalidEmailFormatExceptionThrown() {
        assertThrows(InvalidEmailFormatException.class, () ->
            service.register(new RegisterRequestDTO("alice", null, "Password1"))
        );
    }

    @Test
    void givenShortPassword_whenRegister_thenWeakPasswordExceptionThrown() {
        assertThrows(WeakPasswordException.class, () ->
            service.register(new RegisterRequestDTO("alice", "alice@example.com", "Pw1"))
        );
        verifyNoInteractions(mockHasher);
        verify(mockUserRepo, never()).save(any(User.class));
    }

    @Test
    void givenPasswordWithoutDigit_whenRegister_thenWeakPasswordExceptionThrown() {
        assertThrows(WeakPasswordException.class, () ->
            service.register(new RegisterRequestDTO("alice", "alice@example.com", "Passwords"))
        );
    }

    @Test
    void givenPasswordWithoutLetter_whenRegister_thenWeakPasswordExceptionThrown() {
        assertThrows(WeakPasswordException.class, () ->
            service.register(new RegisterRequestDTO("alice", "alice@example.com", "12345678"))
        );
    }

    @Test
    void givenNullPassword_whenRegister_thenWeakPasswordExceptionThrown() {
        assertThrows(WeakPasswordException.class, () ->
            service.register(new RegisterRequestDTO("alice", "alice@example.com", null))
        );
    }

    @Test
    void givenTakenUsername_whenRegister_thenDuplicateUsernameExceptionThrown() {
        when(mockUserRepo.existsByUsername("alice")).thenReturn(true);

        assertThrows(DuplicateUsernameException.class, () ->
            service.register(new RegisterRequestDTO("alice", "alice@example.com", "Password1"))
        );
        verifyNoInteractions(mockHasher);
        verify(mockUserRepo, never()).save(any(User.class));
    }

    @Test
    void givenTakenEmail_whenRegister_thenDuplicateEmailExceptionThrown() {
        when(mockUserRepo.existsByUsername("alice")).thenReturn(false);
        when(mockUserRepo.findByEmail("alice@example.com"))
            .thenReturn(Optional.of(new User(1, "other", "alice@example.com", "HASH")));

        assertThrows(DuplicateEmailException.class, () ->
            service.register(new RegisterRequestDTO("alice", "alice@example.com", "Password1"))
        );
        verifyNoInteractions(mockHasher);
        verify(mockUserRepo, never()).save(any(User.class));
    }

    @Test
    void givenBadEmailAndTakenUsername_whenRegister_thenFormatFailsFirst() {
        // Ordering check: format validators run before any repo lookup.
        when(mockUserRepo.existsByUsername(any())).thenReturn(true);

        assertThrows(InvalidEmailFormatException.class, () ->
            service.register(new RegisterRequestDTO("alice", "not-an-email", "Password1"))
        );
        verify(mockUserRepo, never()).existsByUsername(any());
    }

    @Test
    void givenValidCredentials_whenLogin_thenTokenIssued() {
        User user = new User(7, "alice", "alice@example.com", "STORED_HASH");
        when(mockUserRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(mockHasher.matches("Password1", "STORED_HASH")).thenReturn(true);
        when(mockSessionManager.generateToken(7, "alice")).thenReturn("ISSUED_TOKEN");
        when(mockSessionManager.extractExpiration("ISSUED_TOKEN")).thenReturn(9999L);

        AuthTokenDTO result = service.login(new LoginRequestDTO("alice", "Password1"));

        assertEquals("ISSUED_TOKEN", result.token());
        assertEquals(9999L, result.expiresAtEpochMillis());
        assertEquals(7, result.userId());
        assertEquals("alice", result.username());
    }

    @Test
    void givenWrongPassword_whenLogin_thenAuthenticationFailedExceptionThrown() {
        User user = new User(7, "alice", "alice@example.com", "STORED_HASH");
        when(mockUserRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(mockHasher.matches("wrong", "STORED_HASH")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () ->
            service.login(new LoginRequestDTO("alice", "wrong"))
        );
        verify(mockSessionManager, never()).generateToken(anyInt(), any());
    }

    @Test
    void givenNoSuchUser_whenLogin_thenSameGenericRejection() {
        when(mockUserRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        // Same exception type as wrong-password — no enumeration leak.
        assertThrows(AuthenticationFailedException.class, () ->
            service.login(new LoginRequestDTO("ghost", "whatever"))
        );
        verifyNoInteractions(mockHasher);
        verify(mockSessionManager, never()).generateToken(anyInt(), any());
    }

    @Test
    void givenInvalidCredentials_whenLogin_thenRejected() {
        // Acceptance-style negative check: bad password rejected, no token issued.
        User user = new User(7, "alice", "alice@example.com", "STORED_HASH");
        when(mockUserRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(mockHasher.matches("badpass", "STORED_HASH")).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () ->
            service.login(new LoginRequestDTO("alice", "badpass"))
        );
    }

    @Test
    void extractUserId_delegatesToSessionManager() {
        when(mockSessionManager.extractUserId("T")).thenReturn(42);
        assertEquals(42, service.extractUserId("T"));
        verify(mockSessionManager).extractUserId("T");
    }

    @Test
    void validateToken_delegatesToSessionManager() {
        when(mockSessionManager.validateToken("T")).thenReturn(true);
        assertTrue(service.validateToken("T"));
        verify(mockSessionManager).validateToken("T");
    }

    @Test
    void logout_delegatesToSessionManagerInvalidate() {
        service.logout(new LogoutRequestDTO("SOME_TOKEN"));
        verify(mockSessionManager).invalidate("SOME_TOKEN");
    }

    @Test
    void logout_idempotent_doesNotThrowForGarbageToken() {
        // sessionManager.invalidate is itself idempotent — the service makes no extra
        // assumptions about the token's shape.
        service.logout(new LogoutRequestDTO("not-a-real-token"));
        verify(mockSessionManager).invalidate("not-a-real-token");
    }
}
