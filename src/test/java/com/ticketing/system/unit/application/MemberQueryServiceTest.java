package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.system.Core.Application.services.MemberQueryService;
import com.ticketing.system.Core.Domain.users.IUserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberQueryServiceTest {

    private IUserRepository userRepository;
    private MemberQueryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        service = new MemberQueryService(userRepository);
    }

    @Test
    void usernameExists_delegatesToRepository() {
        when(userRepository.existsByUsername("naim.founder")).thenReturn(true);

        assertTrue(service.usernameExists("naim.founder"));
    }

    @Test
    void usernameExists_trimsBeforeQuerying() {
        when(userRepository.existsByUsername("naim.founder")).thenReturn(true);

        assertTrue(service.usernameExists("  naim.founder  "));
    }

    @Test
    void usernameExists_returnsFalseForNullOrBlankWithoutHittingRepo() {
        assertFalse(service.usernameExists(null));
        assertFalse(service.usernameExists(""));
        assertFalse(service.usernameExists("   "));
        verify(userRepository, never()).existsByUsername(org.mockito.ArgumentMatchers.anyString());
    }
}
