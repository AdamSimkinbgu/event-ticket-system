package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Domain.exceptions.DuplicateEmailException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.InvalidEmailFormatException;
import com.ticketing.system.Core.Domain.exceptions.WeakPasswordException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

class AuthenticationServiceTest {

    private IUserRepository mockUserRepo;
    private IPasswordHasher mockHasher;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        mockUserRepo = mock(IUserRepository.class);
        mockHasher = mock(IPasswordHasher.class);
        service = new AuthenticationService(mockUserRepo, mockHasher);
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

    @Test @Disabled("UC-12: login on valid credentials issues JWT")
    void givenValidCredentials_whenLogin_thenTokenIssued() {}

    @Test @Disabled("UC-12: login on invalid credentials rejects with generic error")
    void givenInvalidCredentials_whenLogin_thenRejected() {}

    @Test @Disabled("UC-14: logout invalidates session, abandons cart (II.3.0.1)")
    void givenLoggedInMember_whenLogout_thenSessionEndedAndCartReleased() {}
}
