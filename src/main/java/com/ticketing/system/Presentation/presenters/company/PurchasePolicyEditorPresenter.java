package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.EventPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Presentation.components.ErrorPayload;
import com.ticketing.system.Presentation.presenters.ExceptionTranslator;

@Component
public class PurchasePolicyEditorPresenter {

    private final CompanyManagementService companyManagementService;
    private final EventManagementService eventManagementService;

    @Autowired
    public PurchasePolicyEditorPresenter(CompanyManagementService companyManagementService,
                                         EventManagementService eventManagementService) {
        this.companyManagementService = companyManagementService;
        this.eventManagementService = eventManagementService;
    }

    public LoadOutcome load(String token, int companyId, int eventId, boolean isEventLevel) {
        if (token == null) return new LoadOutcome.NotAuthenticated();
        try {
            PurchasePolicyDTO dto = isEventLevel && eventId > 0
                ? eventManagementService.getEventPurchasePolicy(token, companyId, eventId)
                : companyManagementService.getCompanyPurchasePolicy(token, companyId);
            return new LoadOutcome.Success(dto);
        } catch (InvalidTokenException e) {
            return new LoadOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    public SaveOutcome save(String token, int companyId, int eventId, boolean isEventLevel,
                            PurchasePolicyDTO dto) {
        if (token == null) return new SaveOutcome.NotAuthenticated();
        try {
            if (isEventLevel) {
                eventManagementService.setEventPolicies(token,
                        new EventPolicyConfigDTO(companyId, eventId, dto));
            } else {
                companyManagementService.setCompanyPolicies(token,
                        new CompanyPolicyConfigDTO(companyId, dto, List.of()));
            }
            return new SaveOutcome.Success();
        } catch (InvalidTokenException e) {
            return new SaveOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new SaveOutcome.Failure(ExceptionTranslator.toPayload(e));
        }
    }

    public sealed interface LoadOutcome {
        record Success(PurchasePolicyDTO policy) implements LoadOutcome { }
        record NotAuthenticated() implements LoadOutcome { }
        record Failure(ErrorPayload error) implements LoadOutcome { }
    }

    public sealed interface SaveOutcome {
        record Success() implements SaveOutcome { }
        record NotAuthenticated() implements SaveOutcome { }
        record Failure(ErrorPayload error) implements SaveOutcome { }
    }
}
