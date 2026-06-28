package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;

/**
 * MVP presenter for the owner's company event-list view. Loads the caller's first
 * owned company's events and drives event lifecycle actions (cancel-and-refund,
 * delete, status change) via {@link EventManagementService}. Returns sealed
 * outcomes the view renders. Holds no Vaadin imports.
 */
@Component
public class CompanyEventListPresenter {

    private final EventManagementService eventService;
    private final CompanyManagementService companyManagementService;

    @Autowired
    public CompanyEventListPresenter(EventManagementService eventService,
                                     CompanyManagementService companyManagementService) {
        this.eventService = eventService;
        this.companyManagementService = companyManagementService;
    }

    /**
     * Loads the events of the caller's first owned company.
     *
     * @param token the owner's token
     * @return {@link Outcome.Success} with the events; or {@link Outcome.NotAuthenticated},
     *         {@link Outcome.NoCompany}, or {@link Outcome.Failure}
     */
    public Outcome load(String token) {
        if (token == null) return new Outcome.NotAuthenticated();
        try {
            List<ProductionCompanyDTO> owned = companyManagementService.findOwnedCompanies(token);
            if (owned.isEmpty()) return new Outcome.NoCompany();
            int companyId = owned.get(0).companyId();
            List<EventDetailDTO> events = eventService.listEventsForCompany(token, companyId);
            return new Outcome.Success(events);
        } catch (InvalidTokenException e) {
            return new Outcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    /**
     * Cancels an event and runs the refund pipeline.
     *
     * @param token   the owner's token
     * @param eventId the event to cancel
     * @return {@link ActionOutcome.Success}, {@link ActionOutcome.NotAuthenticated},
     *         or {@link ActionOutcome.Failure}
     */
    public ActionOutcome cancelEvent(String token, int eventId) {
        if (token == null) return new ActionOutcome.NotAuthenticated();
        try {
            eventService.cancelEventAndRefund(token, eventId);
            return new ActionOutcome.Success();
        } catch (InvalidTokenException e) {
            return new ActionOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new ActionOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Permanently deletes a CANCELED event with no purchase history.
     *
     * @param token   the owner's token
     * @param eventId the event to delete
     * @return {@link ActionOutcome.Success}, {@link ActionOutcome.NotAuthenticated},
     *         or {@link ActionOutcome.Failure}
     */
    public ActionOutcome deleteEvent(String token, int eventId) {
        if (token == null) return new ActionOutcome.NotAuthenticated();
        try {
            eventService.deleteEvent(token, eventId);
            return new ActionOutcome.Success();
        } catch (InvalidTokenException e) {
            return new ActionOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new ActionOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Changes an event's status (non-cancel transitions only).
     *
     * @param token        the owner's token
     * @param eventId      the event whose status to change
     * @param targetStatus the target status (SCHEDULED or ON_SALE)
     * @return {@link ActionOutcome.Success}, {@link ActionOutcome.NotAuthenticated},
     *         or {@link ActionOutcome.Failure}
     */
    public ActionOutcome changeEventStatus(String token, int eventId, EventStatus targetStatus) {
        if (token == null) return new ActionOutcome.NotAuthenticated();
        try {
            eventService.changeEventStatus(token, eventId, targetStatus);
            return new ActionOutcome.Success();
        } catch (InvalidTokenException e) {
            return new ActionOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new ActionOutcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #load(String)}. */
    public sealed interface Outcome {
        record Success(List<EventDetailDTO> events) implements Outcome {}
        record NotAuthenticated() implements Outcome {}
        record NoCompany() implements Outcome {}
        record Failure(String reason) implements Outcome {}
    }

    /** Result of the event lifecycle actions (cancel/delete/change-status). */
    public sealed interface ActionOutcome {
        record Success() implements ActionOutcome {}
        record NotAuthenticated() implements ActionOutcome {}
        record Failure(String reason) implements ActionOutcome {}
    }
}
