package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

public class CompanyManagementServiceTest {

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
        defaultPermissions.add(Permission.MANAGE_INVENTORY);
        defaultPermissions.add(Permission.VIEW_SALES);
        defaultPermissions.add(Permission.EDIT_POLICIES);
    }

    @Test
    public void testInviteManager_Success() {
        // --- ARRANGE ---
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");
        // Mock Authentication behavior
        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(OWNER_ID);

        // Mock Repository behavior
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        // --- ACT ---
        companyService.inviteManager(VALID_TOKEN, COMPANY_ID, TARGET_USER_ID, defaultPermissions);

        // --- ASSERT ---
        // Verify the database update methods were called exactly once for both entities
        assertEquals(targetUser.getManagementInvitations().size(), 1);

    }

    @Test
    public void testAcceptManagerInvitation_Success() {
        // --- ARRANGE ---
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "password");

        // We must artificially create the "pending" state in our domain objects first
        // so that the accept logic doesn't throw an error!
        company.validateManagerInvitation(COMPANY_ID, TARGET_USER_ID, OWNER_ID, defaultPermissions);
        targetUser.InvitetoCompanyAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

        // Mock Authentication: This time the token belongs to the TARGET USER who is accepting
        when(mockAuthService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockAuthService.extractUserId(VALID_TOKEN)).thenReturn(TARGET_USER_ID);

        // Mock Repositories
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        // --- ACT ---
        companyService.acceptManagerInvitation(VALID_TOKEN, COMPANY_ID);

        // --- ASSERT ---
        // 1. Verify the state was saved
        assertTrue(company.getManagers().containsKey(TARGET_USER_ID));

        // 2. (Optional but recommended) Verify domain state changed correctly
        // If you add a getManagementInvitations() to User, you can assert it's empty now:
        // assertTrue(targetUser.getManagementInvitations().isEmpty());
    }

    @Test
    public void testAcceptManagerInvitation_FailsWithInvalidToken() {
        // --- ARRANGE ---
        String invalidToken = "bad-token";
        when(mockAuthService.validateToken(invalidToken)).thenReturn(false);

        // --- ACT & ASSERT ---
        // We assert that calling the method throws a RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            companyService.acceptManagerInvitation(invalidToken, COMPANY_ID);
        });

        assertEquals("Invalid token", exception.getMessage());

    }

    // --- Skeleton placeholders for the remaining UCs (filled in by the assigned team members) ---

    @Test @Disabled("UC-18: register creates company + initial Founder/Owner appointment")
    void givenAuthenticatedMember_whenRegisterCompany_thenCompanyAndFounderCreated() {}

    @Test @Disabled("UC-21: setCompanyPolicies stores company-wide policies")
    void givenOwner_whenSetCompanyPolicies_thenStored() {}

    @Test @Disabled("UC-23: appointOwner creates PENDING appointment")
    void givenOwner_whenAppointCoOwner_thenPending() {}

    @Test @Disabled("UC-23: respond accepts and activates")
    void givenPendingAppointment_whenAccept_thenActive() {}

    @Test @Disabled("UC-24: editManagerPermissions only by original appointer")
    void givenManager_whenEditByDifferentOwner_thenRejected() {}

    @Test @Disabled("UC-24: revokeManager flips status to REVOKED")
    void givenActiveManager_whenRevoke_thenRevoked() {}

    @Test @Disabled("UC-22: viewSalesHistory returns flat list of company sales")
    void givenOwner_whenViewSalesHistory_thenFlatList() {}

    @Test @Disabled("UC-25: viewOrganizationalTree returns nested tree of ACTIVE appointments")
    void givenOwner_whenViewOrgTree_thenNestedTree() {}
}
