package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.components.ErrorPayload;
import com.ticketing.system.Presentation.presenters.ExceptionTranslator;

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
            return new Outcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    public sealed interface Outcome {
        record Success(List<EventDetailDTO> events) implements Outcome {}
        record NotAuthenticated() implements Outcome {}
        record NoCompany() implements Outcome {}
        record Failure(ErrorPayload error) implements Outcome {}
    }
}