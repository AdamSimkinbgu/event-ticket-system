package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

@SpringBootTest
@ActiveProfiles("test")
class CompanyAcceptanceTest {

    @Autowired private CompanyManagementService companyService;
    @Autowired private AuthenticationService authService;
    @Autowired private IUserRepository userRepository;
    @Autowired private IProductionCompanyRepository companyRepository;
    private static final AtomicInteger companyIdGenerator = new AtomicInteger(1000);
    private List<Permission> defaultPermissions;

    @BeforeEach
    void setUp() {
        defaultPermissions = new ArrayList<>();
        defaultPermissions.add(Permission.CONFIGURE_VENUE);
    }

    private AuthTokenDTO registerAndLoginUser(String username) {
        String sid = authService.startGuestSession().sessionId();
        authService.register(new RegisterRequestDTO(username, username + "@example.com", "Password123!", sid));
        return authService.login(new LoginRequestDTO(username, "Password123!", sid));
    }

    private int createRealCompany(int ownerId) {
        
        int companyId = companyIdGenerator.getAndIncrement();
        ProductionCompany company = new ProductionCompany(companyId, 
                ownerId, 
                "Test Company " + companyId, 
                CompanyStatus.ACTIVE,         
                "Acceptance Test Company",   
                5.0);                      
        companyRepository.updateCompany(company); 
        return companyId;
    }


    @Test
    void GivenOwner_WhenAppointManager_ThenWithPermissions() {
        AuthTokenDTO owner = registerAndLoginUser("ownerAppoint");
        AuthTokenDTO target = registerAndLoginUser("targetAppoint");
        int companyId = createRealCompany(owner.userId());

        companyService.inviteManager(owner.token(), companyId, target.userId(), defaultPermissions);
        companyService.acceptManagerInvitation(target.token(), companyId);

        ProductionCompany dbCompany = companyRepository.getCompanyById(companyId);
        assertTrue(dbCompany.getManagers().containsKey(target.userId()), "Target should be an active manager");
        assertEquals(defaultPermissions, dbCompany.getManagers().get(target.userId()), "Permissions should match");
    }

    @Test
    void GivenAppointer_WhenEditPermissions_ThenUpdated() {
        AuthTokenDTO owner = registerAndLoginUser("ownerEdit");
        AuthTokenDTO target = registerAndLoginUser("targetEdit");
        int companyId = createRealCompany(owner.userId());
        
        companyService.inviteManager(owner.token(), companyId, target.userId(), defaultPermissions);
        companyService.acceptManagerInvitation(target.token(), companyId);

        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.CONFIGURE_VENUE);
        newPermissions.add(Permission.MANAGE_INVENTORY);

        companyService.ModifyManagerPermissions(owner.token(), companyId, target.userId(), newPermissions);

        ProductionCompany dbCompany = companyRepository.getCompanyById(companyId);
        List<Permission> savedPermissions = dbCompany.getManagers().get(target.userId());
        
        assertEquals(2, savedPermissions.size());
        assertTrue(savedPermissions.contains(Permission.CONFIGURE_VENUE));
        assertFalse(savedPermissions.contains(Permission.APPOINT_MANAGER)); // Old permission should be gone
    }


    @Test
    void GivenDifferentOwner_WhenEditPermissions_ThenRejected() {
        AuthTokenDTO actualOwner = registerAndLoginUser("ownerReal");
        AuthTokenDTO target = registerAndLoginUser("targetInnocent");
        AuthTokenDTO fakeOwner = registerAndLoginUser("ownerFake"); 
        
        int companyId = createRealCompany(actualOwner.userId());
        
        companyService.inviteManager(actualOwner.token(), companyId, target.userId(), defaultPermissions);
        companyService.acceptManagerInvitation(target.token(), companyId);

        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.CONFIGURE_VENUE);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            companyService.ModifyManagerPermissions(fakeOwner.token(), companyId, target.userId(), newPermissions);
        });

        assertEquals("Only the owner can modify manager permissions", exception.getMessage());
        
        ProductionCompany dbCompany = companyRepository.getCompanyById(companyId);
        assertEquals(defaultPermissions, dbCompany.getManagers().get(target.userId()), "Permissions should remain unchanged");
    }

    @Test
    void GivenAppointer_WhenRevokeManager_ThenRevoked() {
        AuthTokenDTO owner = registerAndLoginUser("ownerRevoke");
        AuthTokenDTO target = registerAndLoginUser("targetRevoke");
        int companyId = createRealCompany(owner.userId());
        
        companyService.inviteManager(owner.token(), companyId, target.userId(), defaultPermissions);
        companyService.acceptManagerInvitation(target.token(), companyId);

        assertTrue(companyRepository.getCompanyById(companyId).getManagers().containsKey(target.userId()));

        companyService.RevokeManager(owner.token(), companyId, target.userId());

        ProductionCompany dbCompany = companyRepository.getCompanyById(companyId);
        assertFalse(dbCompany.getManagers().containsKey(target.userId()), "Target should no longer be a manager");

        User dbUser = userRepository.getUserById(target.userId());
        assertThrows(RuntimeException.class, () -> {
            dbUser.removeCompanyAppointment(companyId); 
        });
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
    // @Test @Disabled("UC-24 main: appoint Manager with selected permissions")
    // void GivenOwner_WhenAppointManager_ThenWithPermissions() {}
    // @Test @Disabled("UC-24 main: edit Manager permissions by original appointer")
    // void GivenAppointer_WhenEditPermissions_ThenUpdated() {}
    // @Test @Disabled("UC-24 negative: edit by different Owner rejected")
    // void GivenDifferentOwner_WhenEditPermissions_ThenRejected() {}
    // @Test @Disabled("UC-24 main: revoke Manager flips to REVOKED")
    // void GivenAppointer_WhenRevokeManager_ThenRevoked() {}

    // UC-25
    @Test @Disabled("UC-25 main: Owner views organizational tree (ACTIVE only)")
    void GivenOwner_WhenViewOrgTree_ThenNestedActiveOnly() {}
}
