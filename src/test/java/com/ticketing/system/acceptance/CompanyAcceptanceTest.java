package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

@SpringBootTest
@ActiveProfiles("test")
class CompanyAcceptanceTest {
    private CompanyManagementService companyService;
private IProductionCompanyRepository companyRepo;
private IUserRepository userRepo;
private IOrderReceiptRepository orderRepo;
private ISessionManager sessionManager;
private ITicketRepository ticketRepo;
private IEventRepository eventRepo;

private final String OWNER_TOKEN = "owner-token";
private final String MANAGER_TOKEN = "manager-token";
private final String OTHER_OWNER_TOKEN = "other-owner-token";

private final int COMPANY_ID = 100;
private final int OWNER_ID = 1;
private final int MANAGER_ID = 2;
private final int OTHER_OWNER_ID = 3;

private ProductionCompany company;
private User manager;

@BeforeEach
void setUp() {
    companyRepo = mock(IProductionCompanyRepository.class);
    userRepo = mock(IUserRepository.class);
    orderRepo = mock(IOrderReceiptRepository.class);
    sessionManager = mock(ISessionManager.class);
    ticketRepo = mock(ITicketRepository.class);
    eventRepo = mock(IEventRepository.class);

    companyService = new CompanyManagementService(
            companyRepo, userRepo, orderRepo, sessionManager, ticketRepo, eventRepo
    );

    company = new ProductionCompany(
            COMPANY_ID, OWNER_ID, "Test Company",
            CompanyStatus.ACTIVE, "desc", 4.5
    );
    manager = new User(MANAGER_ID, "manager", "m@test.com", "pw");

    when(companyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
    when(userRepo.getUserById(MANAGER_ID)).thenReturn(manager);

    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);

    when(sessionManager.validateToken(OTHER_OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OTHER_OWNER_TOKEN)).thenReturn(OTHER_OWNER_ID);
}

    // UC-18
    @Test @Disabled("UC-18 main: Member registers company → becomes Founder/Owner")
    void GivenMember_WhenRegisterCompany_ThenFounderOwner() {}

    // UC-19
    @Test @Disabled("UC-19 main: Owner adds event to catalog")
    void GivenOwner_WhenAddEvent_ThenInDraft() {}
    @Test @Disabled("UC-19 negative: non-permitted Manager rejected")
    void GivenManagerNoPermission_WhenAddEvent_ThenRejected() {}

    // UC-20
    @Test @Disabled("UC-20 main: Owner binds VenueMap → Tickets pre-generated")
    void GivenEvent_WhenBindMap_ThenTicketsCreated() {}

    // UC-21
    @Test @Disabled("UC-21 main: Owner sets event-level purchase policy")
    void GivenOwner_WhenSetEventPolicy_ThenStored() {}
    @Test @Disabled("UC-21 main: Owner sets company-level discount policy")
    void GivenOwner_WhenSetCompanyPolicy_ThenStored() {}

    // UC-22
    @Test @Disabled("UC-22 main: Owner views company sales — flat list")
    void GivenOwner_WhenViewSales_ThenFlatList() {}
    @Test @Disabled("UC-22 + II.4.5.2: prices reflect time of sale, not current")
    void GivenPriceChanged_WhenViewSales_ThenOriginalPrice() {}

    // UC-23
    @Test @Disabled("UC-23 main: appoint co-Owner → PENDING")
    void GivenOwner_WhenAppointOwner_ThenPending() {}
    @Test @Disabled("UC-23 alt: target accepts → ACTIVE")
    void GivenPending_WhenAccept_ThenActive() {}
    @Test @Disabled("UC-23 negative: cycle prevented (II.4.8.3)")
    void GivenCyclicalAppointment_WhenAttempt_ThenRejected() {}

    // UC-24
        @Test
    void GivenOwner_WhenAppointManager_ThenWithPermissions() {
        List<Permission> permissions = List.of(
                Permission.MANAGE_INVENTORY,
                Permission.CONFIGURE_VENUE
        );

        companyService.inviteManager(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, permissions);

        assertTrue(company.getPendingManagers().containsKey(MANAGER_ID));
        assertEquals(permissions, company.getPendingManagers().get(MANAGER_ID));
        assertEquals(1, manager.getManagementInvitations().size());
    }

    @Test
    void GivenAppointer_WhenEditPermissions_ThenUpdated() {
        List<Permission> oldPermissions = List.of(Permission.MANAGE_INVENTORY);
        List<Permission> newPermissions = List.of(
                Permission.CONFIGURE_VENUE,
                Permission.EDIT_POLICIES
        );

        companyService.inviteManager(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, oldPermissions);
        companyService.acceptManagerInvitation(MANAGER_TOKEN, COMPANY_ID);

        companyService.ModifyManagerPermissions(
                OWNER_TOKEN,
                COMPANY_ID,
                MANAGER_ID,
                newPermissions
        );

        assertEquals(newPermissions, company.getManagers().get(MANAGER_ID));
        assertEquals(newPermissions,
                manager.getAppointmentForCompany(COMPANY_ID).getPermissions());
    }

    @Test
    void GivenDifferentOwner_WhenEditPermissions_ThenRejected() {
        List<Permission> oldPermissions = List.of(Permission.MANAGE_INVENTORY);
        List<Permission> newPermissions = List.of(Permission.EDIT_POLICIES);

        companyService.inviteManager(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, oldPermissions);
        companyService.acceptManagerInvitation(MANAGER_TOKEN, COMPANY_ID);

        assertThrows(RuntimeException.class, () ->
                companyService.ModifyManagerPermissions(
                        OTHER_OWNER_TOKEN,
                        COMPANY_ID,
                        MANAGER_ID,
                        newPermissions
                )
        );

        assertEquals(oldPermissions, company.getManagers().get(MANAGER_ID));
    }

    @Test
    void GivenAppointer_WhenRevokeManager_ThenRevoked() {
        List<Permission> permissions = List.of(Permission.MANAGE_INVENTORY);

        companyService.inviteManager(OWNER_TOKEN, COMPANY_ID, MANAGER_ID, permissions);
        companyService.acceptManagerInvitation(MANAGER_TOKEN, COMPANY_ID);

        companyService.RevokeManager(OWNER_TOKEN, COMPANY_ID, MANAGER_ID);

        assertFalse(company.getManagers().containsKey(MANAGER_ID));
        assertEquals(CompanyRole.None, manager.getMemberProfile().getCompanyRole());
        assertNull(manager.getAppointmentForCompany(COMPANY_ID));
    }

    // UC-25
    @Test @Disabled("UC-25 main: Owner views organizational tree (ACTIVE only)")
    void GivenOwner_WhenViewOrgTree_ThenNestedActiveOnly() {}
}
