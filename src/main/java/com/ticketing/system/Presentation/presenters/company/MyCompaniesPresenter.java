package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.UserCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.CurrentCompanies;

@Component
public class MyCompaniesPresenter {

    private final CompanyManagementService companyManagementService;

    public MyCompaniesPresenter(CompanyManagementService companyManagementService) {
        this.companyManagementService = companyManagementService;
    }

    public Outcome load() {
        Integer userId = AuthSession.userId();
        if (userId == null) return new Outcome.NotAuthenticated();
        try {
            return new Outcome.Success(companyManagementService.listForUser(userId));
        } catch (RuntimeException e) {
            return new Outcome.Failure(e.getMessage());
        }
    }

    public sealed interface Outcome {
        record Success(List<UserCompanyDTO> companies) implements Outcome { }
        record NotAuthenticated() implements Outcome { }
        record Failure(String reason) implements Outcome { }
    }

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

    public boolean isOwnerOf(int companyId) {
        Integer userId = AuthSession.userId();
        if (userId == null) return false;
        return companyManagementService.isOwnerOf(userId, companyId);
    }

    public void selectCompany(int companyId) {
        CurrentCompanies.setCurrentCompany(companyId);
    }
}
