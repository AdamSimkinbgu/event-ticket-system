package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.services.SystemIntegrityVerifier;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.exceptions.SystemIntegrityViolationException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;

class SystemIntegrityVerifierTest {

    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IProductionCompanyRepository companyRepository = mock(IProductionCompanyRepository.class);
    private final SystemIntegrityVerifier verifier = new SystemIntegrityVerifier(userRepository, companyRepository);

    @Test
    void givenEmptySystem_whenVerify_thenPasses() {
        when(companyRepository.findActive()).thenReturn(List.of());
        assertDoesNotThrow(verifier::verify);
    }

    @Test
    void givenActiveCompanyWithOwnerAndKnownProducers_whenVerify_thenPasses() {
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getOwnerIds()).thenReturn(List.of(5));
        when(company.getManagers()).thenReturn(List.of(6));
        when(companyRepository.findActive()).thenReturn(List.of(company));
        when(userRepository.getUserById(5)).thenReturn(mock(User.class));
        when(userRepository.getUserById(6)).thenReturn(mock(User.class));

        assertDoesNotThrow(verifier::verify);
    }

    @Test
    void givenActiveCompanyWithNoOwner_whenVerify_thenThrows() {   // constraint #6
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getOwnerIds()).thenReturn(List.of());
        when(company.getName()).thenReturn("Ghost Co");
        when(companyRepository.findActive()).thenReturn(List.of(company));

        assertThrows(SystemIntegrityViolationException.class, verifier::verify);
    }

    @Test
    void givenProducerThatIsNotAUser_whenVerify_thenThrows() {     // constraint #4
        ProductionCompany company = mock(ProductionCompany.class);
        when(company.getOwnerIds()).thenReturn(List.of(5));
        when(company.getManagers()).thenReturn(List.of());
        when(company.getName()).thenReturn("Phantom Co");
        when(companyRepository.findActive()).thenReturn(List.of(company));
        when(userRepository.getUserById(5)).thenThrow(new UserNotFoundException(5));

        assertThrows(SystemIntegrityViolationException.class, verifier::verify);
    }
}
