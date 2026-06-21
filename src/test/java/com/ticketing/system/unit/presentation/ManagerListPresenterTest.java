package com.ticketing.system.unit.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.AppointmentInfoDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Presentation.presenters.company.ManagerListPresenter;

class ManagerListPresenterTest {

    private static final String TOKEN = "jwt-token";

    private CompanyManagementService service;
    private ManagerListPresenter presenter;

    @BeforeEach
    void setUp() {
        service = mock(CompanyManagementService.class);
        presenter = new ManagerListPresenter(service);
    }

    @Test
    void nullToken_returnsNotAuthenticated_withoutCallingService() {
        ManagerListPresenter.Outcome outcome = presenter.loadRoster(null);

        assertInstanceOf(ManagerListPresenter.Outcome.NotAuthenticated.class, outcome);
        verify(service, never()).findOwnedCompanies(anyString());
    }

    @Test
    void noOwnedCompany_returnsNoCompany() {
        when(service.findOwnedCompanies(TOKEN)).thenReturn(List.of());

        ManagerListPresenter.Outcome outcome = presenter.loadRoster(TOKEN);

        assertInstanceOf(ManagerListPresenter.Outcome.NoCompany.class, outcome);
        verify(service, never()).listManagers(anyString(), anyInt());
    }

    @Test
    void ownedCompany_returnsSuccessWithBothRosters() {
        ProductionCompanyDTO company = new ProductionCompanyDTO(7, "Acme", "desc", "ACTIVE", 1);
        AppointmentInfoDTO manager = new AppointmentInfoDTO(
            "10", 7, "Acme", 2, "carol", 1, "Manager", "ACTIVE",
            List.of("VIEW_SALES"), LocalDateTime.now());
        AppointmentInfoDTO pending = new AppointmentInfoDTO(
            "11", 7, "Acme", 3, "yossi", 1, "Manager", "PENDING",
            List.of("MANAGE_INVENTORY"), LocalDateTime.now());

        when(service.findOwnedCompanies(TOKEN)).thenReturn(List.of(company));
        when(service.listManagers(TOKEN, 7)).thenReturn(List.of(manager));
        when(service.listPendingInvitations(TOKEN, 7)).thenReturn(List.of(pending));

        ManagerListPresenter.Outcome outcome = presenter.loadRoster(TOKEN);

        ManagerListPresenter.Outcome.Success ok =
            assertInstanceOf(ManagerListPresenter.Outcome.Success.class, outcome);
        assertEquals("Acme", ok.companyName());
        assertEquals(List.of(manager), ok.activeManagers());
        assertEquals(List.of(pending), ok.pendingInvitations());
    }

    @Test
    void serviceThrows_returnsFailureWithMessage() {
        when(service.findOwnedCompanies(TOKEN)).thenThrow(new RuntimeException("backend down"));

        ManagerListPresenter.Outcome outcome = presenter.loadRoster(TOKEN);

        ManagerListPresenter.Outcome.Failure fail =
            assertInstanceOf(ManagerListPresenter.Outcome.Failure.class, outcome);
        assertEquals("backend down", fail.reason());
    }
}
