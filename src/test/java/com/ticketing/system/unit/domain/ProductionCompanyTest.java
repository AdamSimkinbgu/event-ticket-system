package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

// Unit tests for the ProductionCompany aggregate.
class ProductionCompanyTest {

     private IProductionCompanyRepository mockCompanyRepo;
    private IUserRepository mockUserRepo;
    private AuthenticationService mockAuthService;
    private CompanyManagementService companyService;

    private final String VALID_TOKEN = "valid-token-123";
    private final int COMPANY_ID = 100;
    private final int OWNER_ID = 1;
    private final int TARGET_USER_ID = 2;
    private List<Permission> defaultPermissions;

    @BeforeEach
    public void setUp() {
        mockCompanyRepo = mock(IProductionCompanyRepository.class);
        mockUserRepo = mock(IUserRepository.class);
        mockAuthService = mock(AuthenticationService.class);

        companyService = new CompanyManagementService(mockCompanyRepo, mockUserRepo, mockAuthService);

        defaultPermissions = new ArrayList<>();
        defaultPermissions.add(Permission.APPOINT_MANAGER);
        defaultPermissions.add(Permission.CONFIGURE_VENUE);
        defaultPermissions.add(Permission.MANAGE_INVENTORY);
    }

    @Test
    public void testInviteManager_Success() {

        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");

        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(OWNER_ID);
        
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(VALID_TOKEN, COMPANY_ID, TARGET_USER_ID, defaultPermissions);

        assertEquals(targetUser.getManagementInvitations().size(), 1);
    }


    @Test
    public void testAcceptManagerInvitation_Success() {

        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");
        
        company.validateManagerInvitation(COMPANY_ID, TARGET_USER_ID, OWNER_ID, defaultPermissions);
        targetUser.InvitetoCompanyAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(TARGET_USER_ID);
        
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.acceptManagerInvitation(VALID_TOKEN, COMPANY_ID);

        assertTrue(company.getManagers().containsKey(TARGET_USER_ID));
    }

    @Test
    public void testRejectManagerInvitation_Success() {
        
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");
        
        company.validateManagerInvitation(COMPANY_ID, TARGET_USER_ID, OWNER_ID, defaultPermissions);
        targetUser.InvitetoCompanyAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(TARGET_USER_ID);
        
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.rejectManagerInvitation(VALID_TOKEN, COMPANY_ID);

        assertTrue(targetUser.getManagementInvitations().isEmpty());
    }

    @Test

    public void testModifyManagerPermissions_Success() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");

        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(OWNER_ID);
        
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(VALID_TOKEN, COMPANY_ID, TARGET_USER_ID, defaultPermissions);
        
        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(TARGET_USER_ID);
        
        companyService.acceptManagerInvitation(VALID_TOKEN, COMPANY_ID);


        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.APPOINT_MANAGER);
        newPermissions.add(Permission.EDIT_POLICIES);


        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(OWNER_ID);
        companyService.ModifyManagerPermissions(VALID_TOKEN, COMPANY_ID, TARGET_USER_ID, newPermissions);

        List<Permission> updatedPermissions = company.getManagers().get(TARGET_USER_ID);
        assertEquals(newPermissions, updatedPermissions);

    }

    @Test
    public void testRevokeManager_Success() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");

        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(OWNER_ID);
        
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(VALID_TOKEN, COMPANY_ID, TARGET_USER_ID, defaultPermissions);
        
        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(TARGET_USER_ID);
        
        companyService.acceptManagerInvitation(VALID_TOKEN, COMPANY_ID);

        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(OWNER_ID);

        companyService.RevokeManager(VALID_TOKEN, COMPANY_ID, TARGET_USER_ID);

        assertTrue(!company.getManagers().containsKey(TARGET_USER_ID));
    }







}
