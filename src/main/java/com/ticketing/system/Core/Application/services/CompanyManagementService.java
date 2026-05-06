package com.ticketing.system.Core.Application.services;

import java.util.List;

import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.IUserRepository;
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




    public int InvitetoCompanyManager(String token, int companyId, int TargetId, List<Permission> permissions) {
        boolean isAuthenticated = authenticationService.validateToken(token);
        int Ownerid = -1;

        if (isAuthenticated) {
            Ownerid = authenticationService.extractUserId(token);
        }else {
            throw new RuntimeException("Invalid token");
        }
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }

        User targetUser = userRepository.getUserById(TargetId);
        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        int result = company.inviteManager(Ownerid, TargetId, permissions);
        targetUser.InvitetoCompanyAppointment(companyId, Ownerid, permissions);

        return result;
    }

}
