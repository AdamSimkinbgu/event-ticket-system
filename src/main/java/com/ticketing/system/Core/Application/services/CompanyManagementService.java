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




}
