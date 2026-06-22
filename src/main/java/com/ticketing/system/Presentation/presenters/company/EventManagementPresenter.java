package com.ticketing.system.Presentation.presenters.company;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;

@Component
public class EventManagementPresenter {

    private final EventManagementService eventService;

    @Autowired
    public EventManagementPresenter(EventManagementService eventService) {
        this.eventService = eventService;
    }

    public LoadOutcome load(String token, int eventId) {
        if (token == null) return new LoadOutcome.NotAuthenticated();
        try {
            EventDetailDTO ev = eventService.getEvent(token, eventId);
            return new LoadOutcome.Success(ev);
        } catch (InvalidTokenException e) {
            return new LoadOutcome.NotAuthenticated();
        } catch (EventNotFoundException e) {
            return new LoadOutcome.NotFound();
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    public SaveOutcome save(String token, EventUpdateDTO dto) {
        if (token == null) return new SaveOutcome.NotAuthenticated();
        try {
            eventService.editEventDetails(token, dto);
            return new SaveOutcome.Success();
        } catch (InvalidTokenException e) {
            return new SaveOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new SaveOutcome.Failure(e.getMessage());
        }
    }

    public CancelOutcome cancel(String token, int eventId) {
        if (token == null) return new CancelOutcome.NotAuthenticated();
        try {
            eventService.cancelEventAndRefund(token, eventId);
            return new CancelOutcome.Success();
        } catch (InvalidTokenException e) {
            return new CancelOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new CancelOutcome.Failure(e.getMessage());
        }
    }

    public sealed interface LoadOutcome {
        record Success(EventDetailDTO event) implements LoadOutcome {}
        record NotAuthenticated() implements LoadOutcome {}
        record NotFound() implements LoadOutcome {}
        record Failure(String reason) implements LoadOutcome {}
    }

    public sealed interface SaveOutcome {
        record Success() implements SaveOutcome {}
        record NotAuthenticated() implements SaveOutcome {}
        record Failure(String reason) implements SaveOutcome {}
    }

    public sealed interface CancelOutcome {
        record Success() implements CancelOutcome {}
        record NotAuthenticated() implements CancelOutcome {}
        record Failure(String reason) implements CancelOutcome {}
    }
}