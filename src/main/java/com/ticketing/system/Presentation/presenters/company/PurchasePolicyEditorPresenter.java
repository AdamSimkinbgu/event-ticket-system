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

/**
 * MVP presenter for the purchase-policy editor (UC-21). Loads and saves either
 * the company-level or event-level purchase policy, routing to
 * {@link CompanyManagementService} or {@link EventManagementService} by the
 * {@code isEventLevel} flag. Returns sealed outcomes the view renders. Holds no
 * Vaadin imports.
 */
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

    /**
     * Loads the company- or event-level purchase policy for editing.
     *
     * @param token        the owner's token
     * @param companyId    the company id
     * @param eventId      the event id (used only when {@code isEventLevel} and &gt; 0)
     * @param isEventLevel whether to load the event-level policy vs the company default
     * @return {@link LoadOutcome.Success} with the policy DTO,
     *         {@link LoadOutcome.NotAuthenticated}, or {@link LoadOutcome.Failure}
     */
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
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Saves the company- or event-level purchase policy.
     *
     * @param token        the owner's token
     * @param companyId    the company id
     * @param eventId      the event id (used only when {@code isEventLevel})
     * @param isEventLevel whether to save the event-level policy vs the company default
     * @param dto          the purchase-policy tree to persist
     * @return {@link SaveOutcome.Success}, {@link SaveOutcome.NotAuthenticated},
     *         or {@link SaveOutcome.Failure}
     */
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
            return new SaveOutcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #load(String, int, int, boolean)}. */
    public sealed interface LoadOutcome {
        record Success(PurchasePolicyDTO policy) implements LoadOutcome { }
        record NotAuthenticated() implements LoadOutcome { }
        record Failure(String reason) implements LoadOutcome { }
    }

    /** Result of {@link #save(String, int, int, boolean, PurchasePolicyDTO)}. */
    public sealed interface SaveOutcome {
        record Success() implements SaveOutcome { }
        record NotAuthenticated() implements SaveOutcome { }
        record Failure(String reason) implements SaveOutcome { }
    }
}
