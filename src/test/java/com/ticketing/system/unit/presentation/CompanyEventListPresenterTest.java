package com.ticketing.system.unit.presentation;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.presenters.company.CompanyEventListPresenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompanyEventListPresenterTest {

    private static final String TOKEN = "owner-token";
    private static final int COMPANY_ID = 42;
    private static final int EVENT_ID = 7;

    private EventManagementService eventService;
    private CompanyManagementService companyService;
    private CompanyEventListPresenter presenter;

    @BeforeEach
    void setUp() {
        eventService = mock(EventManagementService.class);
        companyService = mock(CompanyManagementService.class);
        presenter = new CompanyEventListPresenter(eventService, companyService);
    }

    // ── load ──────────────────────────────────────────────────────────────────

    @Test
    void load_nullToken_returnsNotAuthenticated() {
        assertInstanceOf(CompanyEventListPresenter.Outcome.NotAuthenticated.class,
                presenter.load(null));
        verifyNoInteractions(eventService, companyService);
    }

    @Test
    void load_invalidToken_returnsNotAuthenticated() {
        when(companyService.findOwnedCompanies(TOKEN)).thenThrow(new InvalidTokenException("bad"));

        assertInstanceOf(CompanyEventListPresenter.Outcome.NotAuthenticated.class,
                presenter.load(TOKEN));
    }

    @Test
    void load_noOwnedCompanies_returnsNoCompany() {
        when(companyService.findOwnedCompanies(TOKEN)).thenReturn(List.of());

        assertInstanceOf(CompanyEventListPresenter.Outcome.NoCompany.class,
                presenter.load(TOKEN));
        verifyNoInteractions(eventService);
    }

    @Test
    void load_success_returnsEventsForFirstOwnedCompany() {
        when(companyService.findOwnedCompanies(TOKEN)).thenReturn(List.of(company(COMPANY_ID)));
        List<EventDetailDTO> events = List.of(event(EVENT_ID, "Gala Night"));
        when(eventService.listEventsForCompany(TOKEN, COMPANY_ID)).thenReturn(events);

        CompanyEventListPresenter.Outcome.Success ok =
                assertInstanceOf(CompanyEventListPresenter.Outcome.Success.class, presenter.load(TOKEN));

        assertEquals(1, ok.events().size());
        assertEquals("Gala Night", ok.events().get(0).name());
    }

    @Test
    void load_serviceThrowsRuntime_returnsFailure() {
        when(companyService.findOwnedCompanies(TOKEN)).thenReturn(List.of(company(COMPANY_ID)));
        when(eventService.listEventsForCompany(anyString(), anyInt()))
                .thenThrow(new RuntimeException("db error"));

        CompanyEventListPresenter.Outcome.Failure fail =
                assertInstanceOf(CompanyEventListPresenter.Outcome.Failure.class, presenter.load(TOKEN));

        assertEquals("db error", fail.reason());
    }

    // ── cancelEvent ───────────────────────────────────────────────────────────

    @Test
    void cancelEvent_nullToken_returnsNotAuthenticated() {
        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.NotAuthenticated.class,
                presenter.cancelEvent(null, EVENT_ID));
        verifyNoInteractions(eventService);
    }

    @Test
    void cancelEvent_invalidToken_returnsNotAuthenticated() {
        doThrow(new InvalidTokenException("bad")).when(eventService).cancelEventAndRefund(TOKEN, EVENT_ID);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.NotAuthenticated.class,
                presenter.cancelEvent(TOKEN, EVENT_ID));
    }

    @Test
    void cancelEvent_success_callsServiceAndReturnsSuccess() {
        doNothing().when(eventService).cancelEventAndRefund(TOKEN, EVENT_ID);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Success.class,
                presenter.cancelEvent(TOKEN, EVENT_ID));

        verify(eventService).cancelEventAndRefund(TOKEN, EVENT_ID);
    }

    @Test
    void cancelEvent_serviceThrowsRuntime_returnsFailureWithMessage() {
        doThrow(new RuntimeException("refund failed")).when(eventService).cancelEventAndRefund(TOKEN, EVENT_ID);

        CompanyEventListPresenter.ActionOutcome.Failure fail =
                assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Failure.class,
                        presenter.cancelEvent(TOKEN, EVENT_ID));

        assertEquals("refund failed", fail.reason());
    }

    // ── deleteEvent ───────────────────────────────────────────────────────────

    @Test
    void deleteEvent_nullToken_returnsNotAuthenticated() {
        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.NotAuthenticated.class,
                presenter.deleteEvent(null, EVENT_ID));
        verifyNoInteractions(eventService);
    }

    @Test
    void deleteEvent_invalidToken_returnsNotAuthenticated() {
        doThrow(new InvalidTokenException("bad")).when(eventService).deleteEvent(TOKEN, EVENT_ID);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.NotAuthenticated.class,
                presenter.deleteEvent(TOKEN, EVENT_ID));
    }

    @Test
    void deleteEvent_success_callsServiceAndReturnsSuccess() {
        doNothing().when(eventService).deleteEvent(TOKEN, EVENT_ID);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Success.class,
                presenter.deleteEvent(TOKEN, EVENT_ID));

        verify(eventService).deleteEvent(TOKEN, EVENT_ID);
    }

    @Test
    void deleteEvent_notCanceled_returnsFailureWithMessage() {
        doThrow(new InvalidStateTransitionException("Only CANCELED events can be deleted"))
                .when(eventService).deleteEvent(TOKEN, EVENT_ID);

        CompanyEventListPresenter.ActionOutcome.Failure fail =
                assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Failure.class,
                        presenter.deleteEvent(TOKEN, EVENT_ID));

        assertEquals("Only CANCELED events can be deleted", fail.reason());
    }

    // ── changeEventStatus ─────────────────────────────────────────────────────

    @Test
    void changeEventStatus_nullToken_returnsNotAuthenticated() {
        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.NotAuthenticated.class,
                presenter.changeEventStatus(null, EVENT_ID, EventStatus.ON_SALE));
        verifyNoInteractions(eventService);
    }

    @Test
    void changeEventStatus_invalidToken_returnsNotAuthenticated() {
        doThrow(new InvalidTokenException("bad"))
                .when(eventService).changeEventStatus(TOKEN, EVENT_ID, EventStatus.ON_SALE);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.NotAuthenticated.class,
                presenter.changeEventStatus(TOKEN, EVENT_ID, EventStatus.ON_SALE));
    }

    @Test
    void changeEventStatus_toOnSale_callsServiceAndReturnsSuccess() {
        doNothing().when(eventService).changeEventStatus(TOKEN, EVENT_ID, EventStatus.ON_SALE);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Success.class,
                presenter.changeEventStatus(TOKEN, EVENT_ID, EventStatus.ON_SALE));

        verify(eventService).changeEventStatus(eq(TOKEN), eq(EVENT_ID), eq(EventStatus.ON_SALE));
    }

    @Test
    void changeEventStatus_toScheduled_callsServiceAndReturnsSuccess() {
        doNothing().when(eventService).changeEventStatus(TOKEN, EVENT_ID, EventStatus.SCHEDULED);

        assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Success.class,
                presenter.changeEventStatus(TOKEN, EVENT_ID, EventStatus.SCHEDULED));

        verify(eventService).changeEventStatus(eq(TOKEN), eq(EVENT_ID), eq(EventStatus.SCHEDULED));
    }

    @Test
    void changeEventStatus_serviceThrowsRuntime_returnsFailureWithMessage() {
        doThrow(new RuntimeException("invalid transition"))
                .when(eventService).changeEventStatus(TOKEN, EVENT_ID, EventStatus.ON_SALE);

        CompanyEventListPresenter.ActionOutcome.Failure fail =
                assertInstanceOf(CompanyEventListPresenter.ActionOutcome.Failure.class,
                        presenter.changeEventStatus(TOKEN, EVENT_ID, EventStatus.ON_SALE));

        assertEquals("invalid transition", fail.reason());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ProductionCompanyDTO company(int id) {
        return new ProductionCompanyDTO(id, "Company " + id, "desc", "ACTIVE", 1);
    }

    private static EventDetailDTO event(int id, String name) {
        return new EventDetailDTO(String.valueOf(id), name, null, null, null, null,
                String.valueOf(COMPANY_ID), "Company " + COMPANY_ID, EventStatus.SCHEDULED,
                new ArrayList<>(), new ArrayList<>());
    }
}
