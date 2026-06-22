package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.dto.ZoneDetailDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;

@Component
public class VenueMapPresenter {

    private final EventManagementService eventService;
    private final CompanyManagementService companyManagementService;

    @Autowired
    public VenueMapPresenter(EventManagementService eventService,
                             CompanyManagementService companyManagementService) {
        this.eventService = eventService;
        this.companyManagementService = companyManagementService;
    }

    public LoadOutcome loadZones(String token, int eventId) {
        if (token == null) return new LoadOutcome.NotAuthenticated();
        try {
            List<ZoneDetailDTO> zones = eventService.getEventZones(token, eventId);
            return new LoadOutcome.Success(zones);
        } catch (InvalidTokenException e) {
            return new LoadOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    public SaveOutcome saveMap(String token, String eventId, VenueMapConfigDTO config) {
        if (token == null) return new SaveOutcome.NotAuthenticated();
        try {
            List<ProductionCompanyDTO> owned = companyManagementService.findOwnedCompanies(token);
            if (owned.isEmpty()) return new SaveOutcome.NoCompany();
            int companyId = owned.get(0).companyId();
            eventService.configureVenueMap(token, companyId, config);
            return new SaveOutcome.Success();
        } catch (InvalidTokenException e) {
            return new SaveOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new SaveOutcome.Failure(e.getMessage());
        }
    }

    public sealed interface LoadOutcome {
        record Success(List<ZoneDetailDTO> zones) implements LoadOutcome {}
        record NotAuthenticated() implements LoadOutcome {}
        record Failure(String reason) implements LoadOutcome {}
    }

    public sealed interface SaveOutcome {
        record Success() implements SaveOutcome {}
        record NotAuthenticated() implements SaveOutcome {}
        record NoCompany() implements SaveOutcome {}
        record Failure(String reason) implements SaveOutcome {}
    }
}