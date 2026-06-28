package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.users.Permission;

/**
 * MVP presenter for the manager-invitation view (UC-24). Resolves the invitee and
 * the caller's owned company, then delegates to {@link CompanyManagementService}
 * to create the manager appointment. Returns a sealed {@link Outcome} the view
 * renders. Holds no Vaadin imports.
 */
@Component
public class ManagerInvitationPresenter {

    private final CompanyManagementService companyService;

    @Autowired
    public ManagerInvitationPresenter(CompanyManagementService companyService) {
        this.companyService = companyService;
    }

    /**
     * Invites a member to be a manager (with the given permissions) of the caller's
     * first owned company.
     *
     * @param token           the owner's token
     * @param inviteeUsername the invitee's username or email
     * @param permissions     the permissions to grant the manager
     * @return {@link Outcome.Success}; or {@link Outcome.NotAuthenticated},
     *         {@link Outcome.NoCompany}, {@link Outcome.UserNotFound}, or
     *         {@link Outcome.Failure}
     */
    public Outcome invite(String token, String inviteeUsername, List<Permission> permissions) {
        if (token == null) return new Outcome.NotAuthenticated();
        try {
            List<ProductionCompanyDTO> owned = companyService.findOwnedCompanies(token);
            if (owned.isEmpty()) return new Outcome.NoCompany();
            int companyId = owned.get(0).companyId();
            int targetUserId = companyService.resolveUserId(inviteeUsername);
            companyService.appointManager(token,
                new ManagerAppointmentRequestDTO(companyId, targetUserId, permissions));
            return new Outcome.Success();
        } catch (InvalidTokenException e) {
            return new Outcome.NotAuthenticated();
        } catch (UserNotFoundException e) {
            return new Outcome.UserNotFound(inviteeUsername);
        } catch (RuntimeException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    /** Result of {@link #invite(String, String, List)}. */
    public sealed interface Outcome {
        record Success() implements Outcome {}
        record NotAuthenticated() implements Outcome {}
        record NoCompany() implements Outcome {}
        record UserNotFound(String username) implements Outcome {}
        record Failure(String reason) implements Outcome {}
    }
}