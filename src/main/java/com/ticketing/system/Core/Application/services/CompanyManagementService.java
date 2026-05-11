package com.ticketing.system.Core.Application.services;

import java.util.List;

import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.ManagementInvitation;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

public class CompanyManagementService {
    private final IProductionCompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final AuthenticationService authenticationService;


    public CompanyManagementService(IProductionCompanyRepository companyRepository, IUserRepository userRepository, AuthenticationService authenticationService) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.authenticationService = authenticationService;
    }

    public void inviteManager(String token, int companyId, int targetId, List<Permission> permissions) {
        if (!authenticationService.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }
        int ownerId = authenticationService.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }
        if (company.getOwnerId() != ownerId) {
            throw new RuntimeException("Only the owner can invite managers");
        }
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        company.validateManagerInvitation(companyId, targetId, ownerId, permissions);
        
        targetUser.InvitetoCompanyAppointment(companyId, ownerId, permissions);

        companyRepository.updateCompany(company);
        userRepository.updateUser(targetUser);

    }

    public void acceptManagerInvitation(String token, int companyId) {
        if (!authenticationService.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }
        int targetId = authenticationService.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }

        ManagementInvitation invitation = targetUser.acceptInvitation(companyId);
        company.acceptManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
    }

    public void rejectManagerInvitation(String token, int companyId) {
        if (!authenticationService.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }
        int targetId = authenticationService.extractUserId(token);
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }

        targetUser.rejectInvitation(companyId);
        company.rejectManagerInvitation(targetId);
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);
    }

    public void RevokeManager(String token, int companyId, int targetId) {
        if (!authenticationService.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }
        int ownerId = authenticationService.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }
        if (company.getOwnerId() != ownerId) {
            throw new RuntimeException("Only the owner can revoke managers");
        }
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        company.RevokeManager(targetId);
        targetUser.removeCompanyAppointment(companyId);

        
        userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

    }

    public void ModifyManagerPermissions(String token, int companyId, int targetId, List<Permission> newPermissions) {
        if (!authenticationService.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }
        int ownerId = authenticationService.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }
        if (company.getOwnerId() != ownerId) {
            throw new RuntimeException("Only the owner can modify manager permissions");
        }
        User targetUser = userRepository.getUserById(targetId);
        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        company.ModifyManagerPermissions(companyId, targetId, newPermissions);
        targetUser.ModifyManagerPermissions(companyId, targetId, newPermissions);

         userRepository.updateUser(targetUser);
        companyRepository.updateCompany(company);

    }

    // ---------------------------------------------------------------------------
    // DTO-typed methods added in skeleton round (parallel to the existing
    // token-arg / List<Permission>-arg methods above; team to consolidate later).
    // ---------------------------------------------------------------------------

    // UC-18 — register a new Production Company; appoints Founder/Owner in same transaction.
    public com.ticketing.system.Core.Application.dto.CompanyDTO registerCompany(
            String token,
            com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO request) {
        throw new UnsupportedOperationException("UC-18: not implemented");
    }

    // UC-23 — Owner appoints another Member as co-Owner (PENDING).
    public void appointOwner(
            String token,
            com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO request) {
        throw new UnsupportedOperationException("UC-23: not implemented");
    }

    // UC-23 / UC-24 — target accepts or rejects a pending appointment.
    public void respondToAppointment(
            String token,
            com.ticketing.system.Core.Application.dto.AppointmentResponseDTO response) {
        throw new UnsupportedOperationException("UC-23 / UC-24: not implemented");
    }

    // UC-24 — Owner appoints a Manager with explicit granular permissions.
    public void appointManager(
            String token,
            com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO request) {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-24 — edit a Manager's permission set (only by the original appointer).
    public void editManagerPermissions(
            String token,
            com.ticketing.system.Core.Application.dto.PermissionEditDTO edit) {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-21 — set / replace company-wide default policies.
    public void setCompanyPolicies(
            String token,
            com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }

    // UC-22 — Owner-side flat list of company sales.
    public com.ticketing.system.Core.Application.dto.PageDTO<
            com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO>
            viewSalesHistory(String token, int companyId, int pageNumber, int pageSize) {
        throw new UnsupportedOperationException("UC-22: not implemented");
    }

    // UC-25 — recursive organizational tree (Owners only per II.4.15).
    public com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO
            viewOrganizationalTree(String token, int companyId) {
        throw new UnsupportedOperationException("UC-25: not implemented");
    }

}
