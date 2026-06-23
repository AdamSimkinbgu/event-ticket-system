package com.ticketing.system.Presentation.presenters.company;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.components.ErrorPayload;
import com.ticketing.system.Presentation.presenters.ExceptionTranslator;

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
            EventDetailDTO ev = eventService.getEventDetail(token, eventId);
            return new LoadOutcome.Success(ev);
        } catch (InvalidTokenException e) {
            return new LoadOutcome.NotAuthenticated();
        } catch (EventNotFoundException e) {
            return new LoadOutcome.NotFound();
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(ExceptionTranslator.toPayload(e));
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
            return new SaveOutcome.Failure(ExceptionTranslator.toPayload(e));
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
            return new CancelOutcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    public sealed interface LoadOutcome {
        record Success(EventDetailDTO event) implements LoadOutcome {}
        record NotAuthenticated() implements LoadOutcome {}
        record NotFound() implements LoadOutcome {}
        record Failure(ErrorPayload error) implements LoadOutcome {}
    }

    public sealed interface SaveOutcome {
        record Success() implements SaveOutcome {}
        record NotAuthenticated() implements SaveOutcome {}
        record Failure(ErrorPayload error) implements SaveOutcome {}
    }

    public sealed interface CancelOutcome {
        record Success() implements CancelOutcome {}
        record NotAuthenticated() implements CancelOutcome {}
        record Failure(ErrorPayload error) implements CancelOutcome {}
    }
}