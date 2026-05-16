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

import java.time.LocalDateTime;

import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

public class CompanyManagementServiceTest {

    private IProductionCompanyRepository mockCompanyRepo;
    private IUserRepository mockUserRepo;
    private IOrderReceiptRepository mockOrderReceiptRepo;
    private ISessionManager sessionManager;
    private CompanyManagementService companyService;
    private ITicketRepository ticketRepository;
    private IEventRepository eventRepository;

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
        mockOrderReceiptRepo = mock(IOrderReceiptRepository.class);
        sessionManager = mock(ISessionManager.class);
        ticketRepository = mock(ITicketRepository.class);
        eventRepository = mock(IEventRepository.class);

        companyService = new CompanyManagementService(
                mockCompanyRepo,
                mockUserRepo,
                mockOrderReceiptRepo,
                sessionManager,
                ticketRepository,
                eventRepository
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
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("Epic Productions", "Great movies");
        
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(userId);
        
        when(mockCompanyRepo.existsByName("Epic Productions")).thenReturn(false);
        when(mockCompanyRepo.nextId()).thenReturn(expectedCompanyId);

        // save() is void per IProductionCompanyRepository — no return-value mock needed.
        // The service builds the DTO from the locally-constructed ProductionCompany,
        // so the test verifies via the captured argument below + the returned DTO.

        ProductionCompanyDTO result = companyService.registerCompany(token, request);

        assertNotNull(result);
        assertEquals(expectedCompanyId, result.companyId());
        assertEquals("Epic Productions", result.name());
        
        ArgumentCaptor<ProductionCompany> companyCaptor = ArgumentCaptor.forClass(ProductionCompany.class);
        verify(mockCompanyRepo, times(1)).save(companyCaptor.capture());
        
        ProductionCompany capturedCompany = companyCaptor.getValue();
        assertEquals("Epic Productions", capturedCompany.getName());
        assertEquals(userId, capturedCompany.getOwnerId());
    }

    @Test
    public void GivenInvalidToken_WhenRegisterCompany_ThenThrowException() {
        String token = "invalid-token";
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("Name", "Desc");
        
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
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("   ", "Valid Description");
        
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
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("Existing Name", "Desc");
        
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
        CompanyRegistrationDTO request = new CompanyRegistrationDTO("New Co", "Desc");
        
        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(1);
        when(mockCompanyRepo.existsByName(anyString())).thenReturn(false);
        when(mockCompanyRepo.nextId()).thenReturn(100);
        
        // save() returns void; use Mockito's doThrow().when() pattern for void methods.
        doThrow(new RuntimeException("Database connection lost"))
                .when(mockCompanyRepo).save(any(ProductionCompany.class));

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

    @Test
    public void GivenInvalidToken_WhenViewSalesHistory_ThenThrowException() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.viewSalesHistory(INVALID_TOKEN, COMPANY_ID)
        );
    }

    @Test
    public void GivenCompanyNotFound_WhenViewSalesHistory_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID)
        );
    }

    @Test
    public void GivenUserNotFound_WhenViewSalesHistory_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID)
        );
    }

    @Test
    public void GivenUserWithNoPermission_WhenViewSalesHistory_ThenThrowException() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User requester = mock(User.class);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(requester);
        when(requester.isOwnerInCompany(COMPANY_ID)).thenReturn(false);
        when(requester.hasPermissionInCompany(COMPANY_ID, Permission.VIEW_SALES)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID)
        );
    }

    @Test
    public void GivenOwnerWithNoSales_WhenViewSalesHistory_ThenReturnEmptyList() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User ownerUser = mock(User.class);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(ownerUser.isOwnerInCompany(COMPANY_ID)).thenReturn(true);
        when(mockOrderReceiptRepo.findByCompanyId(COMPANY_ID)).thenReturn(new ArrayList<>());

        List<PurchaseHistoryDTO> result = companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void GivenManagerWithViewSalesPermission_WhenViewSalesHistory_ThenReturnSalesHistory() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User managerUser = mock(User.class);
        OrderReceipt mockReceipt = mock(OrderReceipt.class);
        Ticket ticket = new Ticket(1, 1, 50.0, 10, "BARCODE-001");
        Event mockEvent = mock(Event.class);

        when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(managerUser);
        when(managerUser.isOwnerInCompany(COMPANY_ID)).thenReturn(false);
        when(managerUser.hasPermissionInCompany(COMPANY_ID, Permission.VIEW_SALES)).thenReturn(true);

        when(mockOrderReceiptRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of(mockReceipt));
        when(mockReceipt.getId()).thenReturn(42);
        when(mockReceipt.geteventId()).thenReturn(1);
        when(mockReceipt.getPurchaseTime()).thenReturn(LocalDateTime.now());
        when(ticketRepository.findByOrderReceiptId(42)).thenReturn(List.of(ticket));
        when(eventRepository.findById(1)).thenReturn(mockEvent);
        when(mockEvent.getName()).thenReturn("Rock Concert");

        List<PurchaseHistoryDTO> result = companyService.viewSalesHistory(TARGET_TOKEN, COMPANY_ID);

        assertNotNull(result);
        assertEquals(1, result.size());
        PurchaseHistoryDTO.PurchaseRecordDTO record = result.get(0).records().get(0);
        assertEquals(42, record.orderReceiptId());
        assertEquals(1, record.eventId());
        assertEquals("Rock Concert", record.eventName());
        assertEquals(50.0, record.totalPaid());
        assertEquals(1, record.tickets().size());
    }

    @Test
    public void GivenOwnerWithMultipleSales_WhenViewSalesHistory_ThenReturnAllRecords() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User ownerUser = mock(User.class);
        OrderReceipt mockReceipt1 = mock(OrderReceipt.class);
        OrderReceipt mockReceipt2 = mock(OrderReceipt.class);
        Event mockEvent = mock(Event.class);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(ownerUser.isOwnerInCompany(COMPANY_ID)).thenReturn(true);

        when(mockOrderReceiptRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of(mockReceipt1, mockReceipt2));
        when(mockEvent.getName()).thenReturn("Summer Festival");

        when(mockReceipt1.getId()).thenReturn(1);
        when(mockReceipt1.geteventId()).thenReturn(10);
        when(mockReceipt1.getPurchaseTime()).thenReturn(LocalDateTime.now());
        when(ticketRepository.findByOrderReceiptId(1)).thenReturn(new ArrayList<>());
        when(eventRepository.findById(10)).thenReturn(mockEvent);

        when(mockReceipt2.getId()).thenReturn(2);
        when(mockReceipt2.geteventId()).thenReturn(10);
        when(mockReceipt2.getPurchaseTime()).thenReturn(LocalDateTime.now());
        when(ticketRepository.findByOrderReceiptId(2)).thenReturn(new ArrayList<>());

        List<PurchaseHistoryDTO> result = companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(mockOrderReceiptRepo, times(1)).findByCompanyId(COMPANY_ID);
    }

    @Test @Disabled("UC-25: viewOrganizationalTree returns nested tree of ACTIVE appointments")
    void givenOwner_whenViewOrgTree_thenNestedTree() {}
}