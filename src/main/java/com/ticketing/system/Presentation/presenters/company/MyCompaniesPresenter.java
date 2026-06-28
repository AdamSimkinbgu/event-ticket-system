package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.UserCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.CurrentCompanies;

/**
 * MVP presenter for the "my companies" workspace. Reads the signed-in user from
 * {@link AuthSession}, lists their memberships via {@link CompanyManagementService},
 * tracks the current-company selection via {@link CurrentCompanies}, and supports
 * appointing a co-owner. Holds no Vaadin imports.
 */
@Component
public class MyCompaniesPresenter {

    private final CompanyManagementService companyManagementService;

    public MyCompaniesPresenter(CompanyManagementService companyManagementService) {
        this.companyManagementService = companyManagementService;
    }

    /**
     * Loads the signed-in user's company memberships.
     *
     * @return {@link Outcome.Success} with the memberships,
     *         {@link Outcome.NotAuthenticated}, or {@link Outcome.Failure}
     */
    public Outcome load() {
        Integer userId = AuthSession.userId();
        if (userId == null) return new Outcome.NotAuthenticated();
        try {
            return new Outcome.Success(companyManagementService.listForUser(userId));
        } catch (RuntimeException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #load()}. */
    public sealed interface Outcome {
        record Success(List<UserCompanyDTO> companies) implements Outcome { }
        record NotAuthenticated() implements Outcome { }
        record Failure(String reason) implements Outcome { }
    }

    /**
     * @return the user's currently selected company (the {@link CurrentCompanies}
     *         selection if still a membership, else the first membership), or
     *         {@code null} if not signed in or no memberships
     */
    public UserCompanyDTO currentCompany() {
        Integer userId = AuthSession.userId();
        if (userId == null) return null;
        List<UserCompanyDTO> memberships = companyManagementService.listForUser(userId);
        if (memberships.isEmpty()) return null;
        Integer currentId = CurrentCompanies.currentCompanyId();
        if (currentId != null) {
            for (UserCompanyDTO m : memberships) {
                if (m.companyId() == currentId) return m;
            }
        }
        return memberships.get(0);
    }

    /**
     * @param companyId the company to check
     * @return {@code true} if the signed-in user is an owner of that company
     */
    public boolean isOwnerOf(int companyId) {
        Integer userId = AuthSession.userId();
        if (userId == null) return false;
        return companyManagementService.isOwnerOf(userId, companyId);
    }

    /**
     * Sets the current-company selection for the workspace.
     *
     * @param companyId the company to select
     */
    public void selectCompany(int companyId) {
        CurrentCompanies.setCurrentCompany(companyId);
    }


    /**
     * Appoints another member as a co-owner of the current company.
     *
     * @param inviteeIdentifier the invitee's username or email
     * @return {@link AppointOutcome.Success}; or {@link AppointOutcome.NotAuthenticated},
     *         {@link AppointOutcome.NoCompany}, {@link AppointOutcome.UserNotFound},
     *         or {@link AppointOutcome.Failure}
     */
    public AppointOutcome appoint(String inviteeIdentifier) {
        String token = AuthSession.token();
        if (token == null) return new AppointOutcome.NotAuthenticated();
        try {
            UserCompanyDTO company = currentCompany();
            if (company == null) return new AppointOutcome.NoCompany();
            int targetUserId = companyManagementService.resolveUserId(inviteeIdentifier);
            companyManagementService.appointOwner(token,
                    new OwnerAppointmentRequestDTO(company.companyId(), targetUserId));
            return new AppointOutcome.Success();
        } catch (InvalidTokenException e) {
            return new AppointOutcome.NotAuthenticated();
        } catch (UserNotFoundException e) {
            return new AppointOutcome.UserNotFound(inviteeIdentifier);
        } catch (RuntimeException e) {
            return new AppointOutcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #appoint(String)}. */
    public sealed interface AppointOutcome {
        record Success() implements AppointOutcome {}
        record NotAuthenticated() implements AppointOutcome {}
        record NoCompany() implements AppointOutcome {}
        record UserNotFound(String username) implements AppointOutcome {}
        record Failure(String reason) implements AppointOutcome {}
    }
}
