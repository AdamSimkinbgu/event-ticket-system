package com.ticketing.system.Presentation.presenters.company;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.CurrentCompanies;

/**
 * MVP presenter for the company-registration view (UC-18). Reads the token from
 * {@link AuthSession}, delegates to {@link CompanyManagementService}, sets the
 * newly registered company as current, and returns a sealed {@link Outcome} the
 * view renders. Holds no Vaadin imports.
 */
@Component
public class CompanyRegistrationPresenter {

    private final CompanyManagementService companyManagementService;

    public CompanyRegistrationPresenter(CompanyManagementService companyManagementService) {
        this.companyManagementService = companyManagementService;
    }

    /**
     * Registers a new company for the signed-in user and selects it as current.
     *
     * @param name        the company name
     * @param description the company description
     * @return {@link Outcome.Success} with the new company; or
     *         {@link Outcome.NotAuthenticated}, {@link Outcome.InvalidInput},
     *         {@link Outcome.NameTaken}, or {@link Outcome.Failure}
     */
    public Outcome register(String name, String description) {
        String token = AuthSession.token();
        if (token == null || token.isBlank()) {
            return new Outcome.NotAuthenticated();
        }

        try {
            ProductionCompanyDTO company = companyManagementService.registerCompany(
                    token,
                    new CompanyRegistrationDTO(name, description));
            CurrentCompanies.setCurrentCompany(company.companyId());
            return new Outcome.Success(company);
        } catch (IllegalArgumentException e) {
            return new Outcome.InvalidInput(e.getMessage());
        } catch (IllegalStateException e) {
            return new Outcome.NameTaken(e.getMessage());
        } catch (RuntimeException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #register(String, String)}, switched on by the view. */
    public sealed interface Outcome {
        record Success(ProductionCompanyDTO company) implements Outcome { }

        record NotAuthenticated() implements Outcome { }

        record InvalidInput(String reason) implements Outcome { }

        record NameTaken(String reason) implements Outcome { }

        record Failure(String reason) implements Outcome { }
    }
}
