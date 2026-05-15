package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

public class CompanyManagementServiceTest {

    private IProductionCompanyRepository mockCompanyRepo;
    private IUserRepository mockUserRepo;
    private ISessionManager sessionManager;
    private CompanyManagementService companyService;

    private final String OWNER_TOKEN = "owner-token";
    private final String TARGET_TOKEN = "target-token";
    private final String INVALID_TOKEN = "invalid-token";

    private final int COMPANY_ID = 100;
    private final int OWNER_ID = 1;
    private final String COMPANY_1_NAME = "Company1";
    private final String COMPANY_1_DESCRIPTION = "A test production company1";

    private final int TARGET_USER_ID = 2;
    private List<Permission> defaultPermissions;

    @BeforeEach
    public void setUp() {
        mockCompanyRepo = mock(IProductionCompanyRepository.class);
        mockUserRepo = mock(IUserRepository.class);
        sessionManager = mock(ISessionManager.class);

        companyService = new CompanyManagementService(
                mockCompanyRepo,
                mockUserRepo,
                sessionManager
        );

        defaultPermissions = new ArrayList<>();
        defaultPermissions.add(Permission.APPOINT_MANAGER);
        defaultPermissions.add(Permission.CONFIGURE_VENUE);
        defaultPermissions.add(Permission.MANAGE_INVENTORY);
    }

    
    @Test
    public void GivenOwnerAndTargetUser_WhenInviteManager_ThenTargetHasOneInvitation() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "","password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        assertEquals(1, targetUser.getManagementInvitations().size());
    }

    @Test
    public void GivenPendingManagerInvitation_WhenTargetAccepts_ThenTargetIsCompanyManager() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

        companyService.acceptManagerInvitation(TARGET_TOKEN, COMPANY_ID);

        assertTrue(company.getManagers().containsKey(TARGET_USER_ID));
    }

    @Test
    public void GivenPendingManagerInvitation_WhenTargetRejects_ThenTargetHasNoInvitations() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "","password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

        companyService.rejectManagerInvitation(TARGET_TOKEN, COMPANY_ID);

        assertTrue(targetUser.getManagementInvitations().isEmpty());
    }

    @Test
    public void GivenAcceptedManager_WhenOwnerModifiesPermissions_ThenCompanyPermissionsAreUpdated() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

        companyService.acceptManagerInvitation(TARGET_TOKEN, COMPANY_ID);

        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.APPOINT_MANAGER);
        newPermissions.add(Permission.EDIT_POLICIES);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        companyService.ModifyManagerPermissions(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                newPermissions
        );

        assertEquals(newPermissions, company.getManagers().get(TARGET_USER_ID));
    }

    @Test
    public void GivenAcceptedManager_WhenOwnerRevokesManager_ThenCompanyDoesNotContainManager() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

        companyService.acceptManagerInvitation(TARGET_TOKEN, COMPANY_ID);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        companyService.RevokeManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID
        );

        assertFalse(company.getManagers().containsKey(TARGET_USER_ID));
    }

    @Test
    public void GivenInvalidToken_WhenInviteManager_ThenThrowException() {
        when(sessionManager.validateToken("invalid-token")).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        "invalid-token",
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenCompanyDoesNotExist_WhenInviteManager_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenUserIsNotOwner_WhenInviteManager_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(3);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenTargetUserDoesNotExist_WhenInviteManager_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenEmptyPermissions_WhenInviteManager_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        new ArrayList<>()
                )
        );
    }

    @Test
    public void GivenInvalidToken_WhenAcceptManagerInvitation_ThenThrowException() {
        when(sessionManager.validateToken("invalid-token")).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.acceptManagerInvitation(
                        "invalid-token",
                        COMPANY_ID
                )
        );
    }

    @Test
    public void GivenTargetUserDoesNotExist_WhenAcceptManagerInvitation_ThenThrowException() {
        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                companyService.acceptManagerInvitation(
                        TARGET_TOKEN,
                        COMPANY_ID
                )
        );
    }

    @Test
    public void GivenCompanyDoesNotExist_WhenAcceptManagerInvitation_ThenThrowException() {
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                companyService.acceptManagerInvitation(
                        TARGET_TOKEN,
                        COMPANY_ID
                )
        );
    }

    @Test
    public void GivenNoPendingInvitation_WhenAcceptManagerInvitation_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                companyService.acceptManagerInvitation(
                        TARGET_TOKEN,
                        COMPANY_ID
                )
        );
    }

    @Test
    public void GivenInvalidToken_WhenRejectManagerInvitation_ThenThrowException() {
        when(sessionManager.validateToken("invalid-token")).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.rejectManagerInvitation(
                        "invalid-token",
                        COMPANY_ID
                )
        );
    }

    @Test
    public void GivenNoPendingInvitation_WhenRejectManagerInvitation_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                companyService.rejectManagerInvitation(
                        TARGET_TOKEN,
                        COMPANY_ID
                )
        );
    }

    @Test
    public void GivenInvalidToken_WhenRevokeManager_ThenThrowException() {
        when(sessionManager.validateToken("invalid-token")).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.RevokeManager(
                        "invalid-token",
                        COMPANY_ID,
                        TARGET_USER_ID
                )
        );
    }

    @Test
    public void GivenUserIsNotOwner_WhenRevokeManager_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(3);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                companyService.RevokeManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID
                )
        );
    }

    @Test
    public void GivenTargetIsNotManager_WhenRevokeManager_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        assertThrows(RuntimeException.class, () ->
                companyService.RevokeManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID
                )
        );
    }

    @Test
    public void GivenInvalidToken_WhenModifyManagerPermissions_ThenThrowException() {
        when(sessionManager.validateToken("invalid-token")).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.ModifyManagerPermissions(
                        "invalid-token",
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenUserIsNotOwner_WhenModifyManagerPermissions_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(3);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                companyService.ModifyManagerPermissions(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenTargetIsNotManager_WhenModifyManagerPermissions_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        assertThrows(RuntimeException.class, () ->
                companyService.ModifyManagerPermissions(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenEmptyPermissions_WhenModifyManagerPermissions_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

        companyService.acceptManagerInvitation(
                TARGET_TOKEN,
                COMPANY_ID
        );

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        assertThrows(RuntimeException.class, () ->
                companyService.ModifyManagerPermissions(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        new ArrayList<>()
                )
        );
    }


    @Test
    public void GivenAlreadyPendingInvitation_WhenInviteManagerAgain_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }


    @Test
    public void GivenTargetAlreadyManager_WhenInviteManagerAgain_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User targetUser = new User(TARGET_USER_ID, "targetUser","", "password");

        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        companyService.inviteManager(
                OWNER_TOKEN,
                COMPANY_ID,
                TARGET_USER_ID,
                defaultPermissions
        );

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

        companyService.acceptManagerInvitation(TARGET_TOKEN, COMPANY_ID);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        assertThrows(RuntimeException.class, () ->
                companyService.inviteManager(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        TARGET_USER_ID,
                        defaultPermissions
                )
        );
    }

    @Test
    public void GivenValidData_WhenRegisterCompany_ThenCompanySavedAndDTOReturned() {
        String token = "valid-token";
        int userId = 10;
        int expectedCompanyId = 100;
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("Epic Productions", "Great movies", "email@test.com", "0501234567");
        
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(userId);
        
        when(mockCompanyRepo.existsByName("Epic Productions")).thenReturn(false);
        when(mockCompanyRepo.nextId()).thenReturn(expectedCompanyId);
        
        ProductionCompany savedCompany = mock(ProductionCompany.class);
        when(savedCompany.getId()).thenReturn(expectedCompanyId);
        when(savedCompany.getName()).thenReturn("Epic Productions");
        when(savedCompany.getDescription()).thenReturn("Great movies");
        when(savedCompany.getStatus()).thenReturn(CompanyStatus.ACTIVE); 
        when(savedCompany.getOwnerId()).thenReturn(userId);
        
        when(mockCompanyRepo.save(any(ProductionCompany.class))).thenReturn(savedCompany);

        ProductionCompanyDTO result = companyService.registerCompany(token, request);

        assertNotNull(result);
        assertEquals(expectedCompanyId, result.getId());
        assertEquals("Epic Productions", result.getName());
        
        ArgumentCaptor<ProductionCompany> companyCaptor = ArgumentCaptor.forClass(ProductionCompany.class);
        verify(mockCompanyRepo, times(1)).save(companyCaptor.capture());
        
        ProductionCompany capturedCompany = companyCaptor.getValue();
        assertEquals("Epic Productions", capturedCompany.getName());
        assertEquals(userId, capturedCompany.getOwnerId());
    }

    @Test
    public void GivenInvalidToken_WhenRegisterCompany_ThenThrowException() {
        String token = "invalid-token";
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("Name", "Desc", "email", "phone");
        
        when(sessionManager.validateToken(token)).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            companyService.registerCompany(token, request);
        });
        
        assertEquals("Invalid token", exception.getMessage());
        verify(mockCompanyRepo, never()).save(any());
    }

    @Test
    public void GivenEmptyCompanyName_WhenRegisterCompany_ThenThrowException() {
        String token = "valid-token";
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("   ", "Valid Description", "email", "phone");
        
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            companyService.registerCompany(token, request);
        });
        
        assertTrue(exception.getMessage().contains("All company fields"));
        verify(mockCompanyRepo, never()).save(any());
    }

    @Test
    public void GivenExistingCompanyName_WhenRegisterCompany_ThenThrowException() {
        String token = "valid-token";
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("Existing Name", "Desc", "email", "phone");
        
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(1);
        
        when(mockCompanyRepo.existsByName("Existing Name")).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            companyService.registerCompany(token, request);
        });
        
        assertEquals("A company with this name already exists", exception.getMessage());
        verify(mockCompanyRepo, never()).save(any());
    }

    @Test
    public void GivenDatabaseError_WhenRegisterCompany_ThenThrowException() {
        String token = "valid-token";
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("New Co", "Desc", "email", "phone");
        
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(1);
        when(mockCompanyRepo.existsByName(anyString())).thenReturn(false);
        when(mockCompanyRepo.nextId()).thenReturn(100);
        
        when(mockCompanyRepo.save(any(ProductionCompany.class))).thenThrow(new RuntimeException("Database connection lost"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            companyService.registerCompany(token, request);
        });
        
        assertEquals("Failed to register company due to a server error", exception.getMessage());
        assertEquals("Database connection lost", exception.getCause().getMessage());
    }


    // --- Skeleton placeholders for the remaining UCs (filled in by the assigned team members) ---

    @Test @Disabled("UC-21: setCompanyPolicies stores company-wide policies")
    void givenOwner_whenSetCompanyPolicies_thenStored() {}

    @Test @Disabled("UC-23: appointOwner creates PENDING appointment")
    void givenOwner_whenAppointCoOwner_thenPending() {}

    @Test @Disabled("UC-23: respond accepts and activates")
    void givenPendingAppointment_whenAccept_thenActive() {}

    @Test @Disabled("UC-22: viewSalesHistory returns flat list of company sales")
    void givenOwner_whenViewSalesHistory_thenFlatList() {}

    @Test @Disabled("UC-25: viewOrganizationalTree returns nested tree of ACTIVE appointments")
    void givenOwner_whenViewOrgTree_thenNestedTree() {}
}