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

    public sealed interface Outcome {
        record Success(String companyName, CompanyDashboardDTO stats,
                       PurchaseHistoryDTO sales) implements Outcome {}
        record NotAuthenticated() implements Outcome {}
        record NoCompany() implements Outcome {}
        record Failure(String reason) implements Outcome {}
    }
}
