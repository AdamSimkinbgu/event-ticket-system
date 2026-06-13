package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.AppointmentRevokeDTO;
import com.ticketing.system.Core.Application.dto.AuthTokenDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.EventCreationDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.LoginRequestDTO;
import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;

@SpringBootTest
@ActiveProfiles("test")
class CompanyAcceptanceTest {
        @Autowired
        private AuthenticationService authService;
        @Autowired
        private CompanyManagementService companyService;
        @Autowired
        private EventManagementService eventManagementService;
        @Autowired
        private IProductionCompanyRepository companyRepository;
        @Autowired
        private IEventRepository eventRepository;
        @Autowired
        private ITicketRepository ticketRepository;
        @Autowired
        private IOrderReceiptRepository orderReceiptRepository;
        @Autowired
        private IUserRepository userRepository;

        private AuthTokenDTO registerAndLoginMember(String name) {
                String sid = authService.startGuestSession().sessionId();

    authService.register(new RegisterRequestDTO(
            name,
            name + "@test.com",
            "Password1",
            sid,
            20
    ));

                return authService
                                .login(new LoginRequestDTO(name, "Password1", sid))
                                .authToken();
        }

        // UC-18
        @Test
        @Disabled("UC-18 main: Member registers company → becomes Founder/Owner")
        void GivenMember_WhenRegisterCompany_ThenFounderOwner() {
        }

        // UC-19
        @Test
        @Disabled("UC-19 main: Owner adds event to catalog")
        void GivenOwner_WhenAddEvent_ThenInDraft() {
        }

        @Test
        @Disabled("UC-19 negative: non-permitted Manager rejected")
        void GivenManagerNoPermission_WhenAddEvent_ThenRejected() {
        }

        // UC-20
        @Test
        void GivenOwner_WhenConfigureVenueMapWithCapacity_ThenInventoryZoneCapacityAdded() {
                AuthTokenDTO owner = registerAndLoginMember("capacityOwner");

                int companyId = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("capacityCompany", "desc")).companyId();

   EventCreationDTO eventRequest = new EventCreationDTO(
        companyId,
        "Capacity Test Event",
        "Testing inventory zone capacity",
        EventCategory.CONCERT,
        new Location("Test Venue", "Test City"),
        List.of(new ShowDate(
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(30)
        )),
        null
);
    EventDetailDTO event = eventManagementService.addEvent(owner.token(), eventRequest);

                eventManagementService.configureVenueMap(owner.token(), companyId, new VenueMapConfigDTO(
                                event.eventId(),
                                "Test Venue",
                                List.of(new VenueMapConfigDTO.ZoneConfigDTO("Standing Zone A", false, 100, null,
                                                50.0))));

                Event storedEvent = eventRepository.findById(Integer.parseInt(event.eventId()));

                eventManagementService.updateStandingZoneCapacity(owner.token(), companyId, storedEvent.getId(), 1,
                                150);

                InventoryZone zone = storedEvent.getVenueMap().getInventoryZones().stream()
                                .filter(z -> z.getId() == 1)
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Zone not found"));

                assertEquals(150, zone.getCapacity());

        }

        // UC-21
        @Test
        @Disabled("UC-21 main: Owner sets event-level purchase policy")
        void GivenOwner_WhenSetEventPolicy_ThenStored() {
        }

        @Test
        @Disabled("UC-21 main: Owner sets company-level discount policy")
        void GivenOwner_WhenSetCompanyPolicy_ThenStored() {
        }

        // UC-22
        @Test
        @Disabled("UC-22 main: Owner views company sales — flat list")
        void GivenOwner_WhenViewSales_ThenFlatList() {
        }

        @Test
        @Disabled("UC-22 + II.4.5.2: prices reflect time of sale, not current")
        void GivenPriceChanged_WhenViewSales_ThenOriginalPrice() {
        }

        // UC-23
        @Test
        @Disabled("UC-23 main: appoint co-Owner → PENDING")
        void GivenOwner_WhenAppointOwner_ThenPending() {
        }

        @Test
        @Disabled("UC-23 alt: target accepts → ACTIVE")
        void GivenPending_WhenAccept_ThenActive() {
        }

        @Test
        @Disabled("UC-23 negative: cycle prevented (II.4.8.3)")
        void GivenCyclicalAppointment_WhenAttempt_ThenRejected() {
        }

        // UC-24
        @Test
        void GivenOwner_WhenAppointManager_ThenWithPermissions() {
                AuthTokenDTO owner = registerAndLoginMember("owner24a");
                AuthTokenDTO manager = registerAndLoginMember("manager24a");

                ProductionCompanyDTO companyDto = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("company24a", "desc"));

                int companyId = companyDto.companyId();

                List<Permission> permissions = List.of(
                                Permission.MANAGE_INVENTORY,
                                Permission.CONFIGURE_VENUE);

                companyService.appointManager(owner.token(),
                                new ManagerAppointmentRequestDTO(companyId, manager.userId(), permissions));

                User managerUser = userRepository.getUserById(manager.userId());

                assertNotEquals(null, managerUser.getPendingCompanyAppointment(companyId));
                assertEquals(permissions, managerUser.getPendingCompanyAppointment(companyId).getPermissions().stream()
                                .toList());
        }

        @Test
        void GivenAppointer_WhenEditPermissions_ThenUpdated() {
                AuthTokenDTO owner = registerAndLoginMember("owner24b");
                AuthTokenDTO manager = registerAndLoginMember("manager24b");

                int companyId = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("company24b", "desc")).companyId();

                companyService.appointManager(owner.token(), new ManagerAppointmentRequestDTO(companyId,
                                manager.userId(), List.of(Permission.MANAGE_INVENTORY, Permission.CONFIGURE_VENUE)));

                companyService.respondToAppointment(manager.token(), new AppointmentResponseDTO(companyId, true));

                List<Permission> updated = List.of(
                                Permission.CONFIGURE_VENUE,
                                Permission.EDIT_POLICIES);

                companyService.editManagerPermissions(owner.token(),
                                new PermissionEditDTO(companyId, manager.userId(), updated));

                User managerUser = userRepository.getUserById(manager.userId());

                assertEquals(updated,
                                managerUser.getActiveCompanyAppointment(companyId).getPermissions().stream().toList());
        }

        @Test
        void GivenDifferentOwner_WhenEditPermissions_ThenRejected() {
                AuthTokenDTO owner = registerAndLoginMember("owner24c");
                AuthTokenDTO other = registerAndLoginMember("other24c");
                AuthTokenDTO manager = registerAndLoginMember("manager24c");

                int companyId = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("company24c", "desc")).companyId();

                List<Permission> original = List.of(Permission.MANAGE_INVENTORY);

                companyService.appointManager(owner.token(),
                                new ManagerAppointmentRequestDTO(companyId, manager.userId(), original));

                companyService.respondToAppointment(manager.token(), new AppointmentResponseDTO(companyId, true));

                assertThrows(RuntimeException.class, () -> companyService.editManagerPermissions(
                                other.token(),
                                new PermissionEditDTO(companyId, manager.userId(), List.of(Permission.EDIT_POLICIES))));

                User managerUser = userRepository.getUserById(manager.userId());
                assertEquals(original,
                                managerUser.getActiveCompanyAppointment(companyId).getPermissions().stream().toList());
        }

        @Test
        void GivenAppointer_WhenRevokeManager_ThenRevoked() {
                AuthTokenDTO owner = registerAndLoginMember("owner24d");
                AuthTokenDTO manager = registerAndLoginMember("manager24d");

                int companyId = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("company24d", "desc")).companyId();

                companyService.appointManager(
                                owner.token(),
                                new ManagerAppointmentRequestDTO(companyId, manager.userId(),
                                                List.of(Permission.MANAGE_INVENTORY)));

                companyService.respondToAppointment(manager.token(), new AppointmentResponseDTO(companyId, true));

                companyService.RevokeAppointment(
                                owner.token(),
                                new AppointmentRevokeDTO(companyId, manager.userId()));

                User managerUser = userRepository.getUserById(manager.userId());

                assertNull(managerUser.getActiveCompanyAppointment(companyId));
        }

        // UC-25
        @Test
        @Disabled("UC-25 main: Owner views organizational tree (ACTIVE only)")
        void GivenOwner_WhenViewOrgTree_ThenNestedActiveOnly() {
        }
}
