package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.CompanyDashboardDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.services.CompanyAnalyticsService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;

/**
 * MVP presenter for the company sales dashboard (UC-22). Resolves the caller's
 * first owned company and loads its dashboard stats and sales history from
 * {@link CompanyAnalyticsService}. Returns a sealed {@link Outcome} the view
 * renders. Holds no Vaadin imports.
 */
@Component
public class CompanySalesPresenter {

    private final CompanyManagementService companyManagementService;
    private final CompanyAnalyticsService companyAnalyticsService;

    @Autowired
    public CompanySalesPresenter(CompanyManagementService companyManagementService,
                                  CompanyAnalyticsService companyAnalyticsService) {
        this.companyManagementService = companyManagementService;
        this.companyAnalyticsService = companyAnalyticsService;
    }

    /**
     * Loads the dashboard stats and sales history for the caller's first owned
     * company.
     *
     * @param token the owner's token
     * @return {@link Outcome.Success} with the company name, stats and sales; or
     *         {@link Outcome.NotAuthenticated}, {@link Outcome.NoCompany}, or
     *         {@link Outcome.Failure}
     */
    public Outcome load(String token) {
        if (token == null) return new Outcome.NotAuthenticated();
        try {
            List<ProductionCompanyDTO> owned = companyManagementService.findOwnedCompanies(token);
            if (owned.isEmpty()) return new Outcome.NoCompany();
            int companyId = owned.get(0).companyId();
            CompanyDashboardDTO stats = companyAnalyticsService.dashboard(companyId);
            PurchaseHistoryDTO sales = companyAnalyticsService.salesHistory(companyId);
            return new Outcome.Success(owned.get(0).name(), stats, sales);
        } catch (InvalidTokenException e) {
            return new Outcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #load(String)}. */
    public sealed interface Outcome {
        record Success(String companyName, CompanyDashboardDTO stats,
                       PurchaseHistoryDTO sales) implements Outcome {}
        record NotAuthenticated() implements Outcome {}
        record NoCompany() implements Outcome {}
        record Failure(String reason) implements Outcome {}
    }
}
