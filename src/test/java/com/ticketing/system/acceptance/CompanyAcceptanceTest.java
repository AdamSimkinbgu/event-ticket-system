package com.ticketing.system.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.ticketing.system.Core.Application.dto.PendingInvitationDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.RegisterRequestDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.dto.ZoneDetailDTO;
import com.ticketing.system.Core.Domain.events.EventStatus;
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
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;

import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

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
                                        List.of("Test Artist", "Another Artist"),
                                EventCategory.CONCERT,
                                        4.5,
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

                //eventManagementService.updateStandingZoneCapacity(owner.token(), companyId, storedEvent.getId(), 1, 150);
                //
                eventManagementService.addPlacesToStandingZone(owner.token(), companyId, storedEvent.getId(), 1, 50);
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

        // -------------------------------------------------------------------------
        // getEventDetail — acceptance tests
        // -------------------------------------------------------------------------

        @Test
        void GivenOwnerAddsEvent_WhenGetEventDetail_ThenReturnsExpectedFields() {
                AuthTokenDTO owner = registerAndLoginMember("detailOwner1");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("DetailCo1", "desc")).companyId();

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Summer Festival", "desc", List.of("DJ Max"),
                                EventCategory.FESTIVAL, 4.5,
                                new Location("Germany", "Berlin"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(4))),
                                null));

                EventDetailDTO result = eventManagementService.getEventDetail(owner.token(), created.eventId());

                assertEquals(created.eventId(), result.eventId());
                assertEquals("Summer Festival", result.name());
                assertEquals(EventCategory.FESTIVAL, result.category());
                assertEquals(EventStatus.DRAFT, result.status());
                assertEquals("DetailCo1", result.companyName());
        }

        @Test
        void GivenManagerWithConfigureVenuePermission_WhenGetEventDetail_ThenReturnsDTO() {
                AuthTokenDTO owner = registerAndLoginMember("detailOwner2");
                AuthTokenDTO manager = registerAndLoginMember("detailManager2");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("DetailCo2", "desc")).companyId();

                companyService.appointManager(owner.token(),
                                new ManagerAppointmentRequestDTO(companyId, manager.userId(),
                                                List.of(Permission.CONFIGURE_VENUE)));
                companyService.respondToAppointment(manager.token(), new AppointmentResponseDTO(companyId, true));

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Manager View Event", "desc", List.of("Artist"),
                                EventCategory.MUSIC, 4.0,
                                new Location("France", "Paris"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3))),
                                null));

                EventDetailDTO result = eventManagementService.getEventDetail(manager.token(), created.eventId());

                assertEquals(created.eventId(), result.eventId());
                assertEquals("Manager View Event", result.name());
        }

        @Test
        void GivenUserNotInCompany_WhenGetEventDetail_ThenThrows() {
                AuthTokenDTO owner = registerAndLoginMember("detailOwner3");
                AuthTokenDTO outsider = registerAndLoginMember("outsider3");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("DetailCo3", "desc")).companyId();

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Private Event", "desc", List.of("Artist"),
                                EventCategory.MUSIC, 4.0,
                                new Location("Spain", "Madrid"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3))),
                                null));

                assertThrows(RuntimeException.class,
                                () -> eventManagementService.getEventDetail(outsider.token(), created.eventId()));
        }

        // -------------------------------------------------------------------------
        // editEventDetails — acceptance tests
        // -------------------------------------------------------------------------

        @Test
        void GivenOwner_WhenEditEventName_ThenNameIsUpdatedInRepository() {
                AuthTokenDTO owner = registerAndLoginMember("editOwner1");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("EditCo1", "desc")).companyId();

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Original Name", "desc", List.of("Artist"),
                                EventCategory.MUSIC, 4.0,
                                new Location("Italy", "Rome"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3))),
                                null));

                eventManagementService.editEventDetails(owner.token(),
                                new EventUpdateDTO(created.eventId(), "Updated Name", null, null, null, null));

                Event stored = eventRepository.findById(Integer.parseInt(created.eventId()));
                assertEquals("Updated Name", stored.getName());
        }

        @Test
        void GivenOwner_WhenEditEventCategory_ThenCategoryIsUpdatedInRepository() {
                AuthTokenDTO owner = registerAndLoginMember("editOwner2");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("EditCo2", "desc")).companyId();

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Category Event", "desc", List.of("Artist"),
                                EventCategory.MUSIC, 4.0,
                                new Location("Japan", "Tokyo"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3))),
                                null));

                eventManagementService.editEventDetails(owner.token(),
                                new EventUpdateDTO(created.eventId(), null, null, "SPORTS", null, null));

                Event stored = eventRepository.findById(Integer.parseInt(created.eventId()));
                assertEquals(EventCategory.SPORTS, stored.getCategory());
        }

        @Test
        void GivenOwner_WhenEditNameAndCategoryTogether_ThenBothUpdated() {
                AuthTokenDTO owner = registerAndLoginMember("editOwner3");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("EditCo3", "desc")).companyId();

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Old Name", "desc", List.of("Artist"),
                                EventCategory.MUSIC, 4.0,
                                new Location("Brazil", "Rio"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3))),
                                null));

                eventManagementService.editEventDetails(owner.token(),
                                new EventUpdateDTO(created.eventId(), "New Name", null, "THEATER", null, null));

                Event stored = eventRepository.findById(Integer.parseInt(created.eventId()));
                assertEquals("New Name", stored.getName());
                assertEquals(EventCategory.THEATER, stored.getCategory());
        }

        @Test
        void GivenManagerWithoutConfigureVenuePermission_WhenEditEventDetails_ThenThrows() {
                AuthTokenDTO owner = registerAndLoginMember("editOwner4");
                AuthTokenDTO manager = registerAndLoginMember("editManager4");
                int companyId = companyService.registerCompany(
                                owner.token(), new CompanyRegistrationDTO("EditCo4", "desc")).companyId();

                companyService.appointManager(owner.token(),
                                new ManagerAppointmentRequestDTO(companyId, manager.userId(),
                                                List.of(Permission.VIEW_SALES)));
                companyService.respondToAppointment(manager.token(), new AppointmentResponseDTO(companyId, true));

                EventDetailDTO created = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                                companyId, "Restricted Event", "desc", List.of("Artist"),
                                EventCategory.MUSIC, 4.0,
                                new Location("UK", "London"),
                                List.of(new ShowDate(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(3))),
                                null));

                assertThrows(RuntimeException.class,
                                () -> eventManagementService.editEventDetails(manager.token(),
                                                new EventUpdateDTO(created.eventId(), "Blocked Edit", null, null, null, null)));
        }









        //////////////////////////////////////// Tests for listEventsForCompany
        @Test
        void GivenOwnerAndCompanyHasEvent_WhenListEventsForCompany_ThenReturnsEventDetails() {
        AuthTokenDTO owner = registerAndLoginMember("listOwner1");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("listCompany1", "desc")
        ).companyId();

        EventCreationDTO eventRequest = new EventCreationDTO(
                companyId,
                "Concert",
                "Testing list events",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Test Venue", "Test City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        );

        EventDetailDTO createdEvent =
                eventManagementService.addEvent(owner.token(), eventRequest);

        eventManagementService.configureVenueMap(owner.token(), companyId, new VenueMapConfigDTO(
                createdEvent.eventId(),
                "Test Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO(
                        "Standing Zone A",
                        false,
                        100,
                        null,
                        50.0
                ))
        ));

        List<EventDetailDTO> result =
                eventManagementService.listEventsForCompany(owner.token(), companyId);

        assertNotNull(result);
        assertEquals(1, result.size());

        EventDetailDTO dto = result.get(0);

        assertEquals(createdEvent.eventId(), dto.eventId());
        assertEquals("Concert", dto.name());
        assertEquals(4.5, dto.rating(), 0.001);
        assertEquals(EventCategory.CONCERT, dto.category());
        assertEquals(String.valueOf(companyId), dto.companyId());
        assertEquals("listCompany1", dto.companyName());
        assertEquals(createdEvent.status(), dto.status());
        assertNotNull(dto.showDates());
        assertEquals(1, dto.showDates().size());
        }

        @Test
        void GivenOwnerAndCompanyHasNoEvents_WhenListEventsForCompany_ThenReturnsEmptyList() {
        AuthTokenDTO owner = registerAndLoginMember("listOwner2");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("listCompany2", "desc")
        ).companyId();

        List<EventDetailDTO> result =
                eventManagementService.listEventsForCompany(owner.token(), companyId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        }

        @Test
        void GivenOwnerAndMultipleEvents_WhenListEventsForCompany_ThenReturnsAllEvents() {
        AuthTokenDTO owner = registerAndLoginMember("listOwner3");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("listCompany3", "desc")
        ).companyId();

        EventCreationDTO eventRequest1 = new EventCreationDTO(
                companyId,
                "Concert One",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.0,
                new Location("Venue1", "City1"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        );

        EventCreationDTO eventRequest2 = new EventCreationDTO(
                companyId,
                "Concert Two",
                "desc",
                List.of("Artist2"),
                EventCategory.CONCERT,
                5.0,
                new Location("Venue2", "City2"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(2),
                        LocalDateTime.now().plusDays(2).plusHours(2)
                )),
                null
        );

        EventDetailDTO event1 =
                eventManagementService.addEvent(owner.token(), eventRequest1);

        EventDetailDTO event2 =
                eventManagementService.addEvent(owner.token(), eventRequest2);

        List<EventDetailDTO> result =
                eventManagementService.listEventsForCompany(owner.token(), companyId);

        assertNotNull(result);
        assertEquals(2, result.size());

        List<String> eventIds = result.stream()
                .map(EventDetailDTO::eventId)
                .toList();

        assertTrue(eventIds.contains(event1.eventId()));
        assertTrue(eventIds.contains(event2.eventId()));
        }

        @Test
        void GivenInvalidToken_WhenListEventsForCompany_ThenThrowsException() {
        assertThrows(RuntimeException.class,
                () -> eventManagementService.listEventsForCompany("invalid-token", 999));
        }

        @Test
        void GivenUserWithoutPermission_WhenListEventsForCompany_ThenThrowsException() {
        AuthTokenDTO owner = registerAndLoginMember("listOwner4");
        AuthTokenDTO otherUser = registerAndLoginMember("listOtherUser4");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("listCompany4", "desc")
        ).companyId();

        assertThrows(RuntimeException.class,
                () -> eventManagementService.listEventsForCompany(otherUser.token(), companyId));
        }

        @Test
        void GivenNonExistingCompany_WhenListEventsForCompany_ThenThrowsException() {
        AuthTokenDTO owner = registerAndLoginMember("listOwner5");

        assertThrows(RuntimeException.class,
                () -> eventManagementService.listEventsForCompany(owner.token(), 999999));
        }

        /////////////////////////////////Tests for getEvent
        
        @Test
        void GivenOwnerAndExistingEvent_WhenGetEvent_ThenReturnsEventDetails() {
        AuthTokenDTO owner = registerAndLoginMember("getOwner1");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("getCompany1", "desc")
        ).companyId();

        EventCreationDTO eventRequest = new EventCreationDTO(
                companyId,
                "Concert",
                "Testing get event",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Test Venue", "Test City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        );

        EventDetailDTO createdEvent =
                eventManagementService.addEvent(owner.token(), eventRequest);

        eventManagementService.configureVenueMap(owner.token(), companyId, new VenueMapConfigDTO(
                createdEvent.eventId(),
                "Test Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO(
                        "Standing Zone A",
                        false,
                        100,
                        null,
                        50.0
                ))
        ));

        EventDetailDTO result =
                eventManagementService.getEvent(owner.token(), Integer.parseInt(createdEvent.eventId()));

        assertNotNull(result);
        assertEquals(createdEvent.eventId(), result.eventId());
        assertEquals("Concert", result.name());
        assertEquals(4.5, result.rating(), 0.001);
        assertEquals(EventCategory.CONCERT, result.category());
        assertEquals(String.valueOf(companyId), result.companyId());
        assertEquals("getCompany1", result.companyName());
        assertEquals(createdEvent.status(), result.status());
        assertNotNull(result.showDates());
        assertEquals(1, result.showDates().size());
        }

        @Test
        void GivenEventWithoutVenueMap_WhenGetEvent_ThenLocationIsNull() {
        AuthTokenDTO owner = registerAndLoginMember("getOwner2");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("getCompany2", "desc")
        ).companyId();

        EventDetailDTO createdEvent =
                eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                        companyId,
                        "No Venue Event",
                        "desc",
                        List.of("Artist1"),
                        EventCategory.CONCERT,
                        3.5,
                        new Location("Original Venue", "Original City"),
                        List.of(new ShowDate(
                                LocalDateTime.now().plusDays(1),
                                LocalDateTime.now().plusDays(1).plusHours(2)
                        )),
                        null
                ));

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(null);

        EventDetailDTO result =
                eventManagementService.getEvent(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertEquals(createdEvent.eventId(), result.eventId());
        assertEquals("No Venue Event", result.name());
        assertNull(result.location());
        }

        @Test
        void GivenInvalidToken_WhenGetEvent_ThenThrowsException() {
        assertThrows(RuntimeException.class,
                () -> eventManagementService.getEvent("invalid-token", 999));
        }

        @Test
        void GivenEventDoesNotExist_WhenGetEvent_ThenThrowsException() {
        AuthTokenDTO owner = registerAndLoginMember("getOwner3");

        assertThrows(RuntimeException.class,
                () -> eventManagementService.getEvent(owner.token(), 999999));
        }

        @Test
        void GivenUserWithoutPermission_WhenGetEvent_ThenThrowsException() {
        AuthTokenDTO owner = registerAndLoginMember("getOwner4");
        AuthTokenDTO otherUser = registerAndLoginMember("getOtherUser4");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("getCompany4", "desc")
        ).companyId();

        EventDetailDTO createdEvent =
                eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                        companyId,
                        "Private Event",
                        "desc",
                        List.of("Artist1"),
                        EventCategory.CONCERT,
                        4.5,
                        new Location("Venue", "City"),
                        List.of(new ShowDate(
                                LocalDateTime.now().plusDays(1),
                                LocalDateTime.now().plusDays(1).plusHours(2)
                        )),
                        null
                ));

        assertThrows(RuntimeException.class,
                () -> eventManagementService.getEvent(
                        otherUser.token(),
                        Integer.parseInt(createdEvent.eventId())
                ));
        }


        ///////////////////////////////////////////////Tests for getEventZones
        @Test
        void GivenOwnerAndStandingZone_WhenGetEventZones_ThenReturnsStandingZone() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner1");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany1", "desc")
        ).companyId();

        EventDetailDTO event = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Standing Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        eventManagementService.configureVenueMap(owner.token(), companyId, new VenueMapConfigDTO(
                event.eventId(),
                "Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO(
                        "Standing Zone A",
                        false,
                        100,
                        null,
                        50.0
                ))
        ));

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), Integer.parseInt(event.eventId()));

        assertNotNull(result);
        assertEquals(1, result.size());

        ZoneDetailDTO zone = result.get(0);

        assertEquals("Standing Zone A", zone.name());
        assertFalse(zone.seated());
        assertEquals(0, zone.rows());
        assertEquals(0, zone.seatsPerRow());
        assertEquals(100, zone.capacity());
        assertEquals(50.0, zone.price(), 0.001);
        }

        @Test
        void GivenOwnerAndSeatedZone_WhenGetEventZones_ThenReturnsRowsAndSeatsPerRow() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner2");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany2", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Seated Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        Seat seatA1 = new Seat("A1", 0, 0);
        Seat seatA2 = new Seat("A2", 1, 0);
        Seat seatB1 = new Seat("B1", 0, 1);
        Seat seatB2 = new Seat("B2", 1, 1);

        SeatedZone seatedZone = new SeatedZone(
                1,
                "Seated VIP",
                250.0,
                List.of(seatA1, seatA2, seatB1, seatB2)
        );

        VenueMap venueMap = new VenueMap(
                1,
                new Location("Venue", "City"),
                List.of(seatedZone)
        );

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(venueMap);

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertEquals(1, result.size());

        ZoneDetailDTO zone = result.get(0);

        assertEquals("Seated VIP", zone.name());
        assertTrue(zone.seated());
        assertEquals(2, zone.rows());
        assertEquals(2, zone.seatsPerRow());
        assertEquals(0, zone.capacity());
        assertEquals(250.0, zone.price(), 0.001);
        }

        @Test
        void GivenOwnerAndSeatedZoneWithoutSeats_WhenGetEventZones_ThenRowsAndSeatsPerRowAreZero() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner3");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany3", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Empty Seated Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        SeatedZone seatedZone = new SeatedZone(
                1,
                "Empty Seated Zone",
                150.0,
                List.of()
        );

        VenueMap venueMap = new VenueMap(
                1,
                new Location("Venue", "City"),
                List.of(seatedZone)
        );

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(venueMap);

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertEquals(1, result.size());

        ZoneDetailDTO zone = result.get(0);

        assertEquals("Empty Seated Zone", zone.name());
        assertTrue(zone.seated());
        assertEquals(0, zone.rows());
        assertEquals(0, zone.seatsPerRow());
        assertEquals(0, zone.capacity());
        assertEquals(150.0, zone.price(), 0.001);
        }

        @Test
        void GivenOwnerAndMixedZones_WhenGetEventZones_ThenReturnsAllZones() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner4");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany4", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Mixed Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        SeatedZone seatedZone = new SeatedZone(
                1,
                "Seated Zone",
                200.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0)
                )
        );

        StandingZone standingZone = new StandingZone(
                2,
                "Standing Zone",
                300,
                80.0
        );

        VenueMap venueMap = new VenueMap(
                1,
                new Location("Venue", "City"),
                List.of(seatedZone, standingZone)
        );

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(venueMap);

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertEquals(2, result.size());

        ZoneDetailDTO seatedDto = result.get(0);

        assertEquals("Seated Zone", seatedDto.name());
        assertTrue(seatedDto.seated());
        assertEquals(1, seatedDto.rows());
        assertEquals(2, seatedDto.seatsPerRow());
        assertEquals(0, seatedDto.capacity());
        assertEquals(200.0, seatedDto.price(), 0.001);

        ZoneDetailDTO standingDto = result.get(1);

        assertEquals("Standing Zone", standingDto.name());
        assertFalse(standingDto.seated());
        assertEquals(0, standingDto.rows());
        assertEquals(0, standingDto.seatsPerRow());
        assertEquals(300, standingDto.capacity());
        assertEquals(80.0, standingDto.price(), 0.001);
        }

        @Test
        void GivenEventWithoutVenueMap_WhenGetEventZones_ThenReturnsEmptyList() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner5");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany5", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "No Venue Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(null);

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertTrue(result.isEmpty());
        }

        @Test
        void GivenEventWithEmptyVenueMap_WhenGetEventZones_ThenReturnsEmptyList() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner6");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany6", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Empty Venue Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(new VenueMap(
                1,
                new Location("Venue", "City"),
                List.of()
        ));

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertTrue(result.isEmpty());
        }

        @Test
        void GivenSeatedZoneWithUnevenRows_WhenGetEventZones_ThenUsesIntegerDivisionForSeatsPerRow() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner7");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany7", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Uneven Rows Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        SeatedZone seatedZone = new SeatedZone(
                1,
                "Uneven Seated Zone",
                250.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0),
                        new Seat("A3", 2, 0),
                        new Seat("B1", 0, 1)
                )
        );

        Event storedEvent =
                eventRepository.findById(Integer.parseInt(createdEvent.eventId()));

        storedEvent.setVenueMap(new VenueMap(
                1,
                new Location("Venue", "City"),
                List.of(seatedZone)
        ));

        List<ZoneDetailDTO> result =
                eventManagementService.getEventZones(owner.token(), storedEvent.getId());

        assertNotNull(result);
        assertEquals(1, result.size());

        ZoneDetailDTO zone = result.get(0);

        assertEquals("Uneven Seated Zone", zone.name());
        assertTrue(zone.seated());
        assertEquals(2, zone.rows());
        assertEquals(2, zone.seatsPerRow());
        assertEquals(0, zone.capacity());
        assertEquals(250.0, zone.price(), 0.001);
        }

        @Test
        void GivenInvalidToken_WhenGetEventZones_ThenThrowsException() {
        assertThrows(RuntimeException.class,
                () -> eventManagementService.getEventZones("invalid-token", 999));
        }

        @Test
        void GivenEventDoesNotExist_WhenGetEventZones_ThenThrowsException() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner8");

        assertThrows(RuntimeException.class,
                () -> eventManagementService.getEventZones(owner.token(), 999999));
        }

        @Test
        void GivenUserWithoutPermission_WhenGetEventZones_ThenThrowsException() {
        AuthTokenDTO owner = registerAndLoginMember("zonesOwner9");
        AuthTokenDTO otherUser = registerAndLoginMember("zonesOtherUser9");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("zonesCompany9", "desc")
        ).companyId();

        EventDetailDTO createdEvent = eventManagementService.addEvent(owner.token(), new EventCreationDTO(
                companyId,
                "Private Zones Event",
                "desc",
                List.of("Artist1"),
                EventCategory.CONCERT,
                4.5,
                new Location("Venue", "City"),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(1),
                        LocalDateTime.now().plusDays(1).plusHours(2)
                )),
                null
        ));

        assertThrows(RuntimeException.class,
                () -> eventManagementService.getEventZones(
                        otherUser.token(),
                        Integer.parseInt(createdEvent.eventId())
                ));
        }


        ///////////////////////////////////Tests for listPendingInvitations
        
        @Test
        void GivenUserWithPendingManagerInvitation_WhenListPendingInvitations_ThenReturnsInvitationDTO() {
                AuthTokenDTO owner = registerAndLoginMember("pendingOwner1");
                AuthTokenDTO target = registerAndLoginMember("pendingTarget1");

                int companyId = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("pendingCompany1", "desc"))
                                .companyId();

                int targetUserId = companyService.resolveUserId("pendingTarget1");

                List<Permission> permissions = List.of(
                                Permission.CONFIGURE_VENUE,
                                Permission.MANAGE_INVENTORY);

                companyService.appointManager(owner.token(), new ManagerAppointmentRequestDTO(
                                companyId,
                                targetUserId,
                                permissions));

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(target.token());

                assertNotNull(result);
                assertEquals(1, result.size());

                PendingInvitationDTO dto = result.get(0);

                assertEquals(companyId, dto.companyId());
                assertEquals("pendingCompany1", dto.companyName());
                assertEquals("Manager", dto.role());
                assertEquals(2, dto.permissions().size());
                assertTrue(dto.permissions().contains(Permission.CONFIGURE_VENUE));
                assertTrue(dto.permissions().contains(Permission.MANAGE_INVENTORY));
                assertEquals("pendingOwner1", dto.inviterName());
        }

        @Test
        void GivenUserWithNoPendingInvitations_WhenListPendingInvitations_ThenReturnsEmptyList() {
                AuthTokenDTO user = registerAndLoginMember("noPendingUser1");

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(user.token());

                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        void GivenUserWithMultiplePendingInvitations_WhenListPendingInvitations_ThenReturnsAllPendingInvitations() {
                AuthTokenDTO owner1 = registerAndLoginMember("multiOwner1");
                AuthTokenDTO owner2 = registerAndLoginMember("multiOwner2");
                AuthTokenDTO target = registerAndLoginMember("multiTarget1");

                int companyId1 = companyService.registerCompany(
                                owner1.token(),
                                new CompanyRegistrationDTO("multiCompany1", "desc"))
                                .companyId();

                int companyId2 = companyService.registerCompany(
                                owner2.token(),
                                new CompanyRegistrationDTO("multiCompany2", "desc"))
                                .companyId();

                int targetUserId = companyService.resolveUserId("multiTarget1");

                companyService.appointManager(owner1.token(), new ManagerAppointmentRequestDTO(
                                companyId1,
                                targetUserId,
                                List.of(Permission.CONFIGURE_VENUE)));

                companyService.appointManager(owner2.token(), new ManagerAppointmentRequestDTO(
                                companyId2,
                                targetUserId,
                                List.of(Permission.MANAGE_INVENTORY)));

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(target.token());

                assertNotNull(result);
                assertEquals(2, result.size());

                List<Integer> companyIds = result.stream()
                                .map(PendingInvitationDTO::companyId)
                                .toList();

                assertTrue(companyIds.contains(companyId1));
                assertTrue(companyIds.contains(companyId2));
        }

        @Test
        void GivenUserAcceptedInvitation_WhenListPendingInvitations_ThenAcceptedInvitationIsNotReturned() {
                AuthTokenDTO owner = registerAndLoginMember("acceptedOwner1");
                AuthTokenDTO target = registerAndLoginMember("acceptedTarget1");

                int companyId = companyService.registerCompany(
                                owner.token(),
                                new CompanyRegistrationDTO("acceptedCompany1", "desc"))
                                .companyId();

                int targetUserId = companyService.resolveUserId("acceptedTarget1");

                companyService.appointManager(owner.token(), new ManagerAppointmentRequestDTO(
                                companyId,
                                targetUserId,
                                List.of(Permission.CONFIGURE_VENUE)));

                User targetUser = userRepository.getUserById(targetUserId);
                targetUser.acceptInvitation(companyId);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(target.token());

                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        void GivenUserWithPendingAndAcceptedInvitations_WhenListPendingInvitations_ThenOnlyPendingInvitationReturned() {
                AuthTokenDTO owner1 = registerAndLoginMember("mixedOwner1");
                AuthTokenDTO owner2 = registerAndLoginMember("mixedOwner2");
                AuthTokenDTO target = registerAndLoginMember("mixedTarget1");

                int pendingCompanyId = companyService.registerCompany(
                                owner1.token(),
                                new CompanyRegistrationDTO("pendingMixedCompany", "desc"))
                                .companyId();

                int acceptedCompanyId = companyService.registerCompany(
                                owner2.token(),
                                new CompanyRegistrationDTO("acceptedMixedCompany", "desc"))
                                .companyId();

                int targetUserId = companyService.resolveUserId("mixedTarget1");

                companyService.appointManager(owner1.token(), new ManagerAppointmentRequestDTO(
                                pendingCompanyId,
                                targetUserId,
                                List.of(Permission.CONFIGURE_VENUE)));

                companyService.appointManager(owner2.token(), new ManagerAppointmentRequestDTO(
                                acceptedCompanyId,
                                targetUserId,
                                List.of(Permission.MANAGE_INVENTORY)));

                User targetUser = userRepository.getUserById(targetUserId);
                targetUser.acceptInvitation(acceptedCompanyId);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(target.token());

                assertNotNull(result);
                assertEquals(1, result.size());

                PendingInvitationDTO dto = result.get(0);

                assertEquals(pendingCompanyId, dto.companyId());
                assertEquals("pendingMixedCompany", dto.companyName());
        }

        @Test
        void GivenInvalidToken_WhenListPendingInvitations_ThenThrowsException() {
                assertThrows(RuntimeException.class,
                                () -> companyService.listPendingInvitations("invalid-token"));
        }

        /////////////////////////////////////Tests for resolveUserId
        
        @Test
        void GivenExistingUsername_WhenResolveUserId_ThenReturnsUserId() {
                registerAndLoginMember("resolveUser1");

                int userId = companyService.resolveUserId("resolveUser1");

                User user = userRepository.getUserById(userId);

                assertNotNull(user);
                assertEquals("resolveUser1", user.getUsername());
        }

        @Test
        void GivenExistingEmail_WhenResolveUserId_ThenReturnsUserId() {
                registerAndLoginMember("resolveEmailUser1");

                int userId = companyService.resolveUserId("resolveEmailUser1@test.com");

                User user = userRepository.getUserById(userId);

                assertNotNull(user);
                assertEquals("resolveEmailUser1", user.getUsername());
                assertEquals("resolveEmailUser1@test.com", user.getEmail());
        }

        @Test
        void GivenUsernameWithSpaces_WhenResolveUserId_ThenTrimsAndReturnsUserId() {
                registerAndLoginMember("resolveTrimUser1");

                int userId = companyService.resolveUserId("   resolveTrimUser1   ");

                User user = userRepository.getUserById(userId);

                assertNotNull(user);
                assertEquals("resolveTrimUser1", user.getUsername());
        }

        @Test
        void GivenEmailWithSpaces_WhenResolveUserId_ThenTrimsAndReturnsUserId() {
                registerAndLoginMember("resolveTrimEmailUser1");

                int userId = companyService.resolveUserId("   resolveTrimEmailUser1@test.com   ");

                User user = userRepository.getUserById(userId);

                assertNotNull(user);
                assertEquals("resolveTrimEmailUser1", user.getUsername());
                assertEquals("resolveTrimEmailUser1@test.com", user.getEmail());
        }


        @Test
        void GivenUnknownIdentifier_WhenResolveUserId_ThenThrowsException() {
                assertThrows(RuntimeException.class,
                                () -> companyService.resolveUserId("missing-user"));
        }

        @Test
        void GivenNullIdentifier_WhenResolveUserId_ThenThrowsIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.resolveUserId(null));
        }


        @Test
        void GivenBlankIdentifier_WhenResolveUserId_ThenThrowsIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.resolveUserId("   "));
        }

        @Test
        void GivenEmptyIdentifier_WhenResolveUserId_ThenThrowsIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.resolveUserId(""));
        }


        /////////////////////////////////Test for GetManagerPermissions
        @Test
        void GivenOwnerEditsManagerPermissions_WhenGetManagerPermissions_ThenReturnsUpdatedList() {
        AuthTokenDTO owner = registerAndLoginMember("permOwner1");
        AuthTokenDTO manager = registerAndLoginMember("permManager1");

        int companyId = companyService.registerCompany(
                owner.token(),
                new CompanyRegistrationDTO("permCompany1", "desc")).companyId();

        companyService.appointManager(owner.token(), new ManagerAppointmentRequestDTO(
                companyId,
                manager.userId(),
                List.of(Permission.MANAGE_INVENTORY, Permission.CONFIGURE_VENUE)));

        companyService.respondToAppointment(manager.token(), new AppointmentResponseDTO(companyId, true));

        List<Permission> updated = List.of(Permission.EDIT_POLICIES, Permission.VIEW_SALES);

        companyService.editManagerPermissions(owner.token(),
                new PermissionEditDTO(companyId, manager.userId(), updated));

        List<Permission> result = companyService.getManagerPermissions(owner.token(), companyId, manager.userId());

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsAll(updated));
        }
}