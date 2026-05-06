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



}
