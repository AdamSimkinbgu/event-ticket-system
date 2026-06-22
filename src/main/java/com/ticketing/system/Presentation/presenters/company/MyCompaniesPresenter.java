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

    public List<UserCompanyDTO> listForCurrentUser() {
        Integer userId = AuthSession.userId();
        if (userId == null) return List.of();
        return companyManagementService.listForUser(userId);
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

    public boolean hasAnyMembership() {
        return !listForCurrentUser().isEmpty();
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
