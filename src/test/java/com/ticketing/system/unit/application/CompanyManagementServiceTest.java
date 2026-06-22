package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Application.dto.PermissionEditDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.PurchaseHistoryDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;

import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.AppointmentRevokeDTO;
import com.ticketing.system.Core.Application.dto.CompanyRegistrationDTO;
import com.ticketing.system.Core.Application.dto.ManagerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.OwnerAppointmentRequestDTO;
import com.ticketing.system.Core.Application.dto.PendingInvitationDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.CompanyAppointment;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
//import com.ticketing.system.Core.Domain.users.exceptions.UserNotFoundException;

public class CompanyManagementServiceTest {

        private IProductionCompanyRepository mockCompanyRepo;
        private IUserRepository mockUserRepo;
        private IOrderReceiptRepository mockOrderReceiptRepo;
        private ISessionManager sessionManager;
        private CompanyManagementService companyService;
        private ITicketRepository ticketRepository;
        private IEventRepository eventRepository;
        private INotificationService notificationService;

        private final String OWNER_TOKEN = "owner-token";
        private final String TARGET_TOKEN = "target-token";
        private final String INVALID_TOKEN = "invalid-token";

        private final int COMPANY_ID = 100;
        private final int OWNER_ID = 1;
        private final int ORDER_RECEIPT_ID = 11;
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
                notificationService = mock(INotificationService.class);

                companyService = new CompanyManagementService(
                                mockCompanyRepo,
                                mockUserRepo,
                                mockOrderReceiptRepo,
                                sessionManager,
                                ticketRepository,
                                eventRepository,
                                notificationService);

                defaultPermissions = new ArrayList<>();
                defaultPermissions.add(Permission.CONFIGURE_VENUE);
                defaultPermissions.add(Permission.MANAGE_INVENTORY);

        }

        @Test
        public void GivenOwnerAndTargetUser_WhenInviteManager_ThenTargetHasOneInvitation() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 19);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                companyService.appointManager(OWNER_TOKEN, new ManagerAppointmentRequestDTO(
                                COMPANY_ID, TARGET_USER_ID, defaultPermissions));

                assertNotEquals(null, targetUser.getPendingCompanyAppointment(COMPANY_ID));
        }

        @Test
        public void GivenPendingManagerInvitation_WhenTargetAccepts_ThenTargetIsCompanyManager() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                companyService.appointManager(OWNER_TOKEN, new ManagerAppointmentRequestDTO(
                                COMPANY_ID, TARGET_USER_ID, defaultPermissions));

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                companyService.respondToAppointment(TARGET_TOKEN, new AppointmentResponseDTO(COMPANY_ID, true));

                assertTrue(company.getManagers().contains(TARGET_USER_ID));
        }

        @Test
        public void GivenPendingManagerInvitation_WhenTargetRejects_ThenTargetHasNoInvitations() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 19);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                companyService.appointManager(
                                OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(
                                                COMPANY_ID,
                                                TARGET_USER_ID,
                                                defaultPermissions));

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                companyService.respondToAppointment(TARGET_TOKEN, new AppointmentResponseDTO(COMPANY_ID, false));

                assertTrue(targetUser.getPendingCompanyAppointment(COMPANY_ID) == null);
        }

        @Test
        public void GivenAcceptedManager_WhenOwnerModifiesPermissions_ThenCompanyPermissionsAreUpdated() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                companyService.appointManager(OWNER_TOKEN, new ManagerAppointmentRequestDTO(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                defaultPermissions));

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                companyService.respondToAppointment(TARGET_TOKEN, new AppointmentResponseDTO(COMPANY_ID, true));

                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.EDIT_POLICIES);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                companyService.editManagerPermissions(OWNER_TOKEN,
                                new PermissionEditDTO(COMPANY_ID, TARGET_USER_ID, newPermissions));

                for (Permission perm : newPermissions) {
                        assertTrue(targetUser.hasPermissionInCompany(COMPANY_ID, perm));
                }
        }

        @Test
        public void GivenAcceptedManager_WhenOwnerRevokesManager_ThenCompanyDoesNotContainManager() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                companyService.appointManager(OWNER_TOKEN, new ManagerAppointmentRequestDTO(
                                COMPANY_ID,
                                TARGET_USER_ID,
                                defaultPermissions));

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                companyService.respondToAppointment(TARGET_TOKEN, new AppointmentResponseDTO(COMPANY_ID, true));

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                companyService.RevokeAppointment(OWNER_TOKEN, new AppointmentRevokeDTO(COMPANY_ID, TARGET_USER_ID));

                assertFalse(company.getManagers().contains(TARGET_USER_ID));
        }

        @Test
        public void GivenInvalidToken_WhenInviteManager_ThenThrowException() {
                when(sessionManager.validateToken("invalid-token")).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.appointManager(
                                "invalid-token",
                                new ManagerAppointmentRequestDTO(
                                                COMPANY_ID,
                                                TARGET_USER_ID,
                                                defaultPermissions)));
        }

        @Test
        public void GivenCompanyDoesNotExist_WhenInviteManager_ThenThrowException() {
                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

                assertThrows(RuntimeException.class, () -> companyService.appointManager(
                                OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(
                                                COMPANY_ID,
                                                TARGET_USER_ID,
                                                defaultPermissions)));
        }

        @Test
        public void GivenUserIsNotOwner_WhenInviteManager_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User nonOwnerUser = new User(3, "nonOwner", "", "password", 25);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(3);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(3)).thenReturn(nonOwnerUser);

                assertThrows(RuntimeException.class, () -> companyService.appointManager(
                                OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(
                                                COMPANY_ID,
                                                TARGET_USER_ID,
                                                defaultPermissions)));
        }

        @Test
        public void GivenTargetUserDoesNotExist_WhenInviteManager_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(null);

                assertThrows(RuntimeException.class, () -> companyService.appointManager(
                                OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(
                                                COMPANY_ID,
                                                TARGET_USER_ID,
                                                defaultPermissions)));
        }

        @Test
        public void GivenEmptyPermissions_WhenInviteManager_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                assertThrows(RuntimeException.class, () -> companyService.appointManager(
                                OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(
                                                COMPANY_ID,
                                                TARGET_USER_ID,
                                                new ArrayList<>())));
        }

        @Test
        public void GivenInvalidToken_WhenAcceptManagerInvitation_ThenThrowException() {
                when(sessionManager.validateToken("invalid-token")).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.respondToAppointment(
                                "invalid-token",
                                new AppointmentResponseDTO(COMPANY_ID, true)));
        }

        @Test
        public void GivenTargetUserDoesNotExist_WhenAcceptManagerInvitation_ThenThrowException() {
                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(null);

                assertThrows(RuntimeException.class, () -> companyService.respondToAppointment(
                                TARGET_TOKEN,
                                new AppointmentResponseDTO(COMPANY_ID, true)));
        }

        @Test
        public void GivenCompanyDoesNotExist_WhenAcceptManagerInvitation_ThenThrowException() {
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

                assertThrows(RuntimeException.class, () -> companyService.respondToAppointment(
                                TARGET_TOKEN,
                                new AppointmentResponseDTO(COMPANY_ID, true)));
        }

        @Test
        public void GivenNoPendingInvitation_WhenAcceptManagerInvitation_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

                assertThrows(RuntimeException.class, () -> companyService.respondToAppointment(
                                TARGET_TOKEN,
                                new AppointmentResponseDTO(COMPANY_ID, true)));
        }

        @Test
        public void GivenInvalidToken_WhenRejectManagerInvitation_ThenThrowException() {
                when(sessionManager.validateToken("invalid-token")).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.respondToAppointment(
                                "invalid-token",
                                new AppointmentResponseDTO(COMPANY_ID, false)));
        }

        @Test
        public void GivenNoPendingInvitation_WhenRejectManagerInvitation_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

                assertThrows(RuntimeException.class, () -> companyService.respondToAppointment(
                                TARGET_TOKEN,
                                new AppointmentResponseDTO(COMPANY_ID, false)));
        }

        @Test
        public void GivenInvalidToken_WhenRevokeManager_ThenThrowException() {
                when(sessionManager.validateToken("invalid-token")).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.RevokeAppointment(
                                "invalid-token",
                                new AppointmentRevokeDTO(COMPANY_ID, TARGET_USER_ID)));
        }

        @Test
        public void GivenUserIsNotOwner_WhenRevokeManager_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User nonOwnerUser = new User(3, "nonOwner", "", "password", 22);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 22);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(3);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(3)).thenReturn(nonOwnerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                assertThrows(RuntimeException.class, () -> companyService.RevokeAppointment(
                                OWNER_TOKEN,
                                new AppointmentRevokeDTO(COMPANY_ID, TARGET_USER_ID)));
        }

        @Test
        public void GivenTargetIsNotManager_WhenRevokeManager_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                assertThrows(RuntimeException.class, () -> companyService.RevokeAppointment(
                                OWNER_TOKEN,
                                new AppointmentRevokeDTO(COMPANY_ID, TARGET_USER_ID)));
        }

        @Test
        public void GivenInvalidToken_WhenModifyManagerPermissions_ThenThrowException() {
                when(sessionManager.validateToken("invalid-token")).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.editManagerPermissions(
                                "invalid-token",
                                new PermissionEditDTO(COMPANY_ID, TARGET_USER_ID, defaultPermissions)));
        }

        @Test
        public void GivenUserIsNotOwner_WhenModifyManagerPermissions_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 25);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(3);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                assertThrows(RuntimeException.class, () -> companyService.editManagerPermissions(
                                OWNER_TOKEN,
                                new PermissionEditDTO(COMPANY_ID, TARGET_USER_ID, defaultPermissions)));
        }

        @Test
        public void GivenTargetIsNotManager_WhenModifyManagerPermissions_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                // editManagerPermissions only fetches the target user (not the owner), so no
                // owner mock needed
                assertThrows(RuntimeException.class, () -> companyService.editManagerPermissions(
                                OWNER_TOKEN,
                                new PermissionEditDTO(COMPANY_ID, TARGET_USER_ID, defaultPermissions)));
        }

        @Test
        public void GivenEmptyPermissions_WhenModifyManagerPermissions_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                companyService.appointManager(OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(COMPANY_ID, TARGET_USER_ID, defaultPermissions));

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                companyService.respondToAppointment(
                                TARGET_TOKEN, new AppointmentResponseDTO(COMPANY_ID, true));

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

                assertThrows(RuntimeException.class, () -> companyService.editManagerPermissions(
                                OWNER_TOKEN,
                                new PermissionEditDTO(COMPANY_ID, TARGET_USER_ID, new ArrayList<>())));
        }

        @Test
        public void GivenAlreadyPendingInvitation_WhenInviteManagerAgain_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
                ownerUser.addFounderAppointment(COMPANY_ID);
                User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                companyService.appointManager(OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(COMPANY_ID, TARGET_USER_ID, defaultPermissions));

                assertThrows(RuntimeException.class, () -> companyService.appointManager(OWNER_TOKEN,
                                new ManagerAppointmentRequestDTO(COMPANY_ID, TARGET_USER_ID, defaultPermissions)));
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

                when(mockUserRepo.getUserById(userId)).thenReturn(new User(userId, "founder", "", "password", 30));

                // save() is void per IProductionCompanyRepository — no return-value mock
                // needed.
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

                assertEquals("Invalid authentication token: Invalid token", exception.getMessage());
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

        @Test
        public void GivenInvalidToken_WhenViewSalesHistory_ThenThrowException() {
                when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.viewSalesHistory(INVALID_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenCompanyNotFound_WhenViewSalesHistory_ThenThrowException() {
                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

                assertThrows(RuntimeException.class, () -> companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenUserNotFound_WhenViewSalesHistory_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(null);

                assertThrows(RuntimeException.class, () -> companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenUserWithNoPermission_WhenViewSalesHistory_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User requester = mock(User.class);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(requester);
                when(requester.isOwnerInCompany(COMPANY_ID)).thenReturn(false);
                when(requester.hasPermissionInCompany(COMPANY_ID, Permission.VIEW_SALES)).thenReturn(false);

                assertThrows(RuntimeException.class, () -> companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenOwnerWithNoSales_WhenViewSalesHistory_ThenReturnEmptyList() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = mock(User.class);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(eventRepository.findIdsByCompany(COMPANY_ID)).thenReturn(new ArrayList<>());
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(ownerUser.hasPermissionInCompany(COMPANY_ID, Permission.VIEW_SALES)).thenReturn(true);
                // when(mockOrderReceiptRepo.findByCompanyId(COMPANY_ID)).thenReturn(new
                // ArrayList<>());

                List<PurchaseHistoryDTO> result = companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        public void GivenManagerWithViewSalesPermission_WhenViewSalesHistory_ThenReturnSalesHistory() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User managerUser = mock(User.class);
                OrderReceipt mockReceipt = mock(OrderReceipt.class);
                Ticket ticket = new Ticket(1, 1, ORDER_RECEIPT_ID, null, 50.0, 10, "BARCODE-001");
                Event mockEvent = mock(Event.class);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(managerUser);
                when(managerUser.isOwnerInCompany(COMPANY_ID)).thenReturn(false);
                when(managerUser.hasPermissionInCompany(COMPANY_ID, Permission.VIEW_SALES)).thenReturn(true);

                when(mockReceipt.getId()).thenReturn(42);
                when(mockReceipt.getPurchaseTime()).thenReturn(LocalDateTime.now());
                ReceiptLine mockLine = mock(ReceiptLine.class);
                when(mockLine.getTicketId()).thenReturn(10);
                when(mockLine.getPriceAtReservation()).thenReturn(50.0);
                when(mockReceipt.getReceiptLines()).thenReturn(List.of(mockLine));
                when(ticketRepository.findByOrderReceiptId(42)).thenReturn(List.of(ticket));
                when(eventRepository.findById(1)).thenReturn(mockEvent);
                when(mockEvent.getName()).thenReturn("Rock Concert");
                when(eventRepository.findIdsByCompany(COMPANY_ID)).thenReturn(List.of(1));
                when(mockOrderReceiptRepo.findByEventIds(List.of(1))).thenReturn(List.of(mockReceipt));

                List<PurchaseHistoryDTO> result = companyService.viewSalesHistory(TARGET_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertEquals(1, result.size());
                PurchaseHistoryDTO.PurchaseRecordDTO record = result.get(0).records().get(0);
                assertEquals(42, record.orderReceiptId());
                // assertEquals(1, record.eventId());
                // assertEquals("Rock Concert", record.eventName());
                assertEquals(50.0, record.totalPaid());
                assertEquals(1, record.tickets().size());
        }

        @Test
        public void GivenOwnerWithMultipleSales_WhenViewSalesHistory_ThenReturnAllRecords() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User ownerUser = mock(User.class);
                OrderReceipt mockReceipt1 = mock(OrderReceipt.class);
                OrderReceipt mockReceipt2 = mock(OrderReceipt.class);
                Event mockEvent = mock(Event.class);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(ownerUser.hasPermissionInCompany(COMPANY_ID, Permission.VIEW_SALES)).thenReturn(true);

                when(mockEvent.getName()).thenReturn("Summer Festival");

                when(mockReceipt1.getId()).thenReturn(1);
                when(mockReceipt1.getPurchaseTime()).thenReturn(LocalDateTime.now());
                when(ticketRepository.findByOrderReceiptId(1)).thenReturn(new ArrayList<>());
                when(eventRepository.findById(10)).thenReturn(mockEvent);

                when(mockReceipt2.getId()).thenReturn(2);
                when(mockReceipt2.getPurchaseTime()).thenReturn(LocalDateTime.now());
                when(ticketRepository.findByOrderReceiptId(2)).thenReturn(new ArrayList<>());
                when(eventRepository.findIdsByCompany(COMPANY_ID)).thenReturn(List.of(10));
                when(mockOrderReceiptRepo.findByEventIds(List.of(10))).thenReturn(List.of(mockReceipt1, mockReceipt2));

                List<PurchaseHistoryDTO> result = companyService.viewSalesHistory(OWNER_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertEquals(2, result.size());
        }

        @Test
        public void GivenInvalidToken_WhenViewOrganizationalTree_ThenThrowException() {
                when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

                assertThrows(RuntimeException.class,
                                () -> companyService.viewOrganizationalTree(INVALID_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenCompanyNotFound_WhenViewOrganizationalTree_ThenThrowException() {
                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

                assertThrows(RuntimeException.class,
                                () -> companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenUserNotFound_WhenViewOrganizationalTree_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(null);

                assertThrows(RuntimeException.class,
                                () -> companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenUserIsNotOwner_WhenViewOrganizationalTree_ThenThrowException() {
                ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
                User nonOwnerUser = mock(User.class);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(nonOwnerUser);
                when(nonOwnerUser.isOwnerInCompany(COMPANY_ID)).thenReturn(false);

                assertThrows(RuntimeException.class,
                                () -> companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID));
        }

        @Test
        public void GivenOwnerWithNoManagers_WhenViewOrganizationalTree_ThenReturnRootOnlyNode() {
                ProductionCompany company = mock(ProductionCompany.class);
                when(company.getManagers()).thenReturn(new ArrayList<>());
                when(company.getFounderId()).thenReturn(OWNER_ID);
                when(company.getOwnersIds()).thenReturn(List.of(OWNER_ID));

                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 30);
                ownerUser.addFounderAppointment(COMPANY_ID);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);

                OrganizationalTreeNodeDTO result = companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertEquals(OWNER_ID, result.userId());
                assertEquals("ownerUser", result.username());
                assertEquals("Owner", result.role());
                assertTrue(result.isFounder());
                assertTrue(result.grantedPermissions().isEmpty());
                assertTrue(result.appointedByThisUser().isEmpty());
        }

        @Test
        public void GivenOwnerWithOneDirectManager_WhenViewOrganizationalTree_ThenReturnTreeWithOneChild() {
                List<Integer> managersMap = new ArrayList<>();
                managersMap.add(TARGET_USER_ID);

                ProductionCompany company = mock(ProductionCompany.class);
                when(company.getManagers()).thenReturn(managersMap);
                when(company.getFounderId()).thenReturn(OWNER_ID);
                when(company.getOwnersIds()).thenReturn(List.of(OWNER_ID));

                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 30);
                ownerUser.addFounderAppointment(COMPANY_ID);

                User managerUser = new User(TARGET_USER_ID, "managerUser", "", "password", 85);
                managerUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);
                managerUser.acceptInvitation(COMPANY_ID);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(managerUser);

                OrganizationalTreeNodeDTO result = companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertEquals(OWNER_ID, result.userId());
                assertEquals(1, result.appointedByThisUser().size());

                OrganizationalTreeNodeDTO managerNode = result.appointedByThisUser().get(0);
                assertEquals(TARGET_USER_ID, managerNode.userId());
                assertEquals("managerUser", managerNode.username());
                assertEquals("Manager", managerNode.role());
                assertFalse(managerNode.isFounder());
                assertEquals(defaultPermissions.size(), managerNode.grantedPermissions().size());
                assertTrue(managerNode.appointedByThisUser().isEmpty());
        }

        @Test
        public void GivenOwnerWithTwoDirectManagers_WhenViewOrganizationalTree_ThenReturnTreeWithTwoChildren() {
                int MANAGER1_ID = TARGET_USER_ID;
                int MANAGER2_ID = 3;

                List<Integer> managersMap = new ArrayList<>();
                managersMap.add(MANAGER1_ID);
                managersMap.add(MANAGER2_ID);

                ProductionCompany company = mock(ProductionCompany.class);
                when(company.getManagers()).thenReturn(managersMap);
                when(company.getFounderId()).thenReturn(OWNER_ID);
                when(company.getOwnersIds()).thenReturn(List.of(OWNER_ID));

                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 30);
                ownerUser.addFounderAppointment(COMPANY_ID);

                User manager1 = new User(MANAGER1_ID, "manager1", "", "password", 101);
                manager1.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);
                manager1.acceptInvitation(COMPANY_ID);

                User manager2 = new User(MANAGER2_ID, "manager2", "", "password", 102);
                manager2.receiveManagerAppointment(COMPANY_ID, OWNER_ID, List.of(Permission.VIEW_SALES));
                manager2.acceptInvitation(COMPANY_ID);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(MANAGER1_ID)).thenReturn(manager1);
                when(mockUserRepo.getUserById(MANAGER2_ID)).thenReturn(manager2);

                OrganizationalTreeNodeDTO result = companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertEquals(OWNER_ID, result.userId());
                assertEquals(2, result.appointedByThisUser().size());

                List<Integer> childIds = result.appointedByThisUser().stream()
                                .map(OrganizationalTreeNodeDTO::userId)
                                .toList();
                assertTrue(childIds.contains(MANAGER1_ID));
                assertTrue(childIds.contains(MANAGER2_ID));
                result.appointedByThisUser().forEach(child -> {
                        assertEquals("Manager", child.role());
                        assertFalse(child.isFounder());
                        assertTrue(child.appointedByThisUser().isEmpty());
                });
        }

        @Test
        public void GivenManagerWhoAppointedSubManager_WhenViewOrganizationalTree_ThenReturnMultiLevelTree() {
                int MANAGER1_ID = TARGET_USER_ID;
                int MANAGER2_ID = 3;

                List<Integer> managersMap = new ArrayList<>();
                managersMap.add(MANAGER1_ID);
                managersMap.add(MANAGER2_ID);

                ProductionCompany company = mock(ProductionCompany.class);
                when(company.getManagers()).thenReturn(managersMap);
                when(company.getFounderId()).thenReturn(OWNER_ID);
                when(company.getOwnersIds()).thenReturn(List.of(OWNER_ID));

                User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 30);
                ownerUser.addFounderAppointment(COMPANY_ID);

                // manager1 was appointed by the owner
                User manager1 = new User(MANAGER1_ID, "manager1", "", "password", 101);
                manager1.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);
                manager1.acceptInvitation(COMPANY_ID);

                // manager2 was appointed by manager1, not by the owner
                User manager2 = new User(MANAGER2_ID, "manager2", "", "password", 152);
                manager2.receiveManagerAppointment(COMPANY_ID, MANAGER1_ID, List.of(Permission.MANAGE_INVENTORY));
                manager2.acceptInvitation(COMPANY_ID);

                when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockUserRepo.getUserById(MANAGER1_ID)).thenReturn(manager1);
                when(mockUserRepo.getUserById(MANAGER2_ID)).thenReturn(manager2);

                OrganizationalTreeNodeDTO result = companyService.viewOrganizationalTree(OWNER_TOKEN, COMPANY_ID);

                assertNotNull(result);
                assertEquals(OWNER_ID, result.userId());
                assertEquals(1, result.appointedByThisUser().size());

                OrganizationalTreeNodeDTO manager1Node = result.appointedByThisUser().get(0);
                assertEquals(MANAGER1_ID, manager1Node.userId());
                assertEquals("manager1", manager1Node.username());
                assertEquals("Manager", manager1Node.role());
                assertFalse(manager1Node.isFounder());
                assertEquals(1, manager1Node.appointedByThisUser().size());

                OrganizationalTreeNodeDTO manager2Node = manager1Node.appointedByThisUser().get(0);
                assertEquals(MANAGER2_ID, manager2Node.userId());
                assertEquals("manager2", manager2Node.username());
                assertEquals("Manager", manager2Node.role());
                assertFalse(manager2Node.isFounder());
                assertTrue(manager2Node.appointedByThisUser().isEmpty());
                assertEquals(List.of(Permission.MANAGE_INVENTORY), manager2Node.grantedPermissions());
        }


        /////////////////////////////////////Tests for listPendingInvitations
        

        @Test
        public void GivenUserWithPendingInvitation_WhenListPendingInvitations_ThenReturnsInvitationDTO() {
                String TARGET_TOKEN = "target-token";

                ProductionCompany company = new ProductionCompany(
                                COMPANY_ID,
                                OWNER_ID,
                                COMPANY_1_NAME,
                                CompanyStatus.ACTIVE,
                                COMPANY_1_DESCRIPTION,
                                4.5);

                User ownerUser = new User(OWNER_ID, "ownerUser", "owner@test.com", "password", 22);

                User targetUser = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);
                targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(TARGET_TOKEN);

                assertNotNull(result);
                assertEquals(1, result.size());

                PendingInvitationDTO dto = result.get(0);

                assertEquals(COMPANY_ID, dto.companyId());
                assertEquals(COMPANY_1_NAME, dto.companyName());
                assertEquals("Manager", dto.role());
                assertEquals(defaultPermissions.size(), dto.permissions().size());
                assertTrue(dto.permissions().contains(Permission.CONFIGURE_VENUE));
                assertTrue(dto.permissions().contains(Permission.MANAGE_INVENTORY));
                assertEquals("ownerUser", dto.inviterName());
        }

        @Test
        public void GivenUserWithNoPendingInvitations_WhenListPendingInvitations_ThenReturnsEmptyList() {
                String TARGET_TOKEN = "target-token";

                User targetUser = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(TARGET_TOKEN);

                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        public void GivenUserWithAcceptedInvitation_WhenListPendingInvitations_ThenAcceptedInvitationIsNotReturned() {
                String TARGET_TOKEN = "target-token";

                User targetUser = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);
                targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);
                targetUser.acceptInvitation(COMPANY_ID);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(TARGET_TOKEN);

                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        public void GivenUserWithPendingInvitationAndMissingCompany_WhenListPendingInvitations_ThenCompanyNameIsUnknownCompany() {
                String TARGET_TOKEN = "target-token";

                User ownerUser = new User(OWNER_ID, "ownerUser", "owner@test.com", "password", 22);

                User targetUser = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);
                targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(ownerUser);
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(TARGET_TOKEN);

                assertNotNull(result);
                assertEquals(1, result.size());

                PendingInvitationDTO dto = result.get(0);

                assertEquals(COMPANY_ID, dto.companyId());
                assertEquals("Unknown company", dto.companyName());
                assertEquals("ownerUser", dto.inviterName());
        }

        @Test
        public void GivenUserWithPendingInvitationAndMissingInviter_WhenListPendingInvitations_ThenInviterNameIsUnknown() {
                String TARGET_TOKEN = "target-token";

                ProductionCompany company = new ProductionCompany(
                                COMPANY_ID,
                                OWNER_ID,
                                COMPANY_1_NAME,
                                CompanyStatus.ACTIVE,
                                COMPANY_1_DESCRIPTION,
                                4.5);

                User targetUser = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);
                targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockUserRepo.getUserById(OWNER_ID)).thenThrow(new RuntimeException("User not found"));
                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(TARGET_TOKEN);

                assertNotNull(result);
                assertEquals(1, result.size());

                PendingInvitationDTO dto = result.get(0);

                assertEquals(COMPANY_ID, dto.companyId());
                assertEquals(COMPANY_1_NAME, dto.companyName());
                assertEquals("Unknown", dto.inviterName());
        }

        @Test
        public void GivenUserWithMultiplePendingInvitations_WhenListPendingInvitations_ThenReturnsAllPendingInvitations() {
                String TARGET_TOKEN = "target-token";

                int COMPANY_2_ID = 200;
                int OWNER_2_ID = 300;

                ProductionCompany company1 = new ProductionCompany(
                                COMPANY_ID,
                                OWNER_ID,
                                COMPANY_1_NAME,
                                CompanyStatus.ACTIVE,
                                COMPANY_1_DESCRIPTION,
                                4.5);

                ProductionCompany company2 = new ProductionCompany(
                                COMPANY_2_ID,
                                OWNER_2_ID,
                                "Company2",
                                CompanyStatus.ACTIVE,
                                "Second company",
                                4.0);

                User owner1 = new User(OWNER_ID, "ownerUser", "owner@test.com", "password", 22);
                User owner2 = new User(OWNER_2_ID, "ownerUser2", "owner2@test.com", "password", 25);

                User targetUser = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);
                targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);
                targetUser.receiveManagerAppointment(COMPANY_2_ID, OWNER_2_ID, List.of(Permission.VIEW_SALES));

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);

                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);
                when(mockUserRepo.getUserById(OWNER_ID)).thenReturn(owner1);
                when(mockUserRepo.getUserById(OWNER_2_ID)).thenReturn(owner2);

                when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company1);
                when(mockCompanyRepo.getCompanyById(COMPANY_2_ID)).thenReturn(company2);

                List<PendingInvitationDTO> result =
                                companyService.listPendingInvitations(TARGET_TOKEN);

                assertNotNull(result);
                assertEquals(2, result.size());

                List<Integer> companyIds = result.stream()
                                .map(PendingInvitationDTO::companyId)
                                .toList();

                assertTrue(companyIds.contains(COMPANY_ID));
                assertTrue(companyIds.contains(COMPANY_2_ID));
        }

        @Test
        public void GivenInvalidToken_WhenListPendingInvitations_ThenThrowsException() {
                when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

                assertThrows(RuntimeException.class,
                                () -> companyService.listPendingInvitations(INVALID_TOKEN));
        }

        @Test
        public void GivenValidTokenButUserDoesNotExist_WhenListPendingInvitations_ThenThrowsException() {
                String TARGET_TOKEN = "target-token";

                when(sessionManager.validateToken(TARGET_TOKEN)).thenReturn(true);
                when(sessionManager.extractUserId(TARGET_TOKEN)).thenReturn(TARGET_USER_ID);
                when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(null);

                assertThrows(RuntimeException.class,
                                () -> companyService.listPendingInvitations(TARGET_TOKEN));
        }

        //////////////////////////////Tests for resolveUserId
        
        @Test
        public void GivenUsername_WhenResolveUserId_ThenReturnsUserId() {
                User user = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);

                when(mockUserRepo.findByUsername("targetUser")).thenReturn(Optional.of(user));

                int result = companyService.resolveUserId("targetUser");

                assertEquals(TARGET_USER_ID, result);
        }

        @Test
        public void GivenEmail_WhenResolveUserId_ThenReturnsUserId() {
                User user = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);

                when(mockUserRepo.findByUsername("target@test.com")).thenReturn(Optional.empty());
                when(mockUserRepo.findByEmail("target@test.com")).thenReturn(Optional.of(user));

                int result = companyService.resolveUserId("target@test.com");

                assertEquals(TARGET_USER_ID, result);
        }

        @Test
        public void GivenIdentifierWithSpaces_WhenResolveUserId_ThenTrimsIdentifierAndReturnsUserId() {
                User user = new User(TARGET_USER_ID, "targetUser", "target@test.com", "password", 19);

                when(mockUserRepo.findByUsername("targetUser")).thenReturn(Optional.of(user));

                int result = companyService.resolveUserId("   targetUser   ");

                assertEquals(TARGET_USER_ID, result);
        }

        @Test
        public void GivenIdentifierMatchesUsernameAndEmail_WhenResolveUserId_ThenUsernameHasPriority() {
                User usernameUser = new User(10, "sameIdentifier", "first@test.com", "password", 20);
                User emailUser = new User(20, "otherUser", "sameIdentifier", "password", 21);

                when(mockUserRepo.findByUsername("sameIdentifier")).thenReturn(Optional.of(usernameUser));
                when(mockUserRepo.findByEmail("sameIdentifier")).thenReturn(Optional.of(emailUser));

                int result = companyService.resolveUserId("sameIdentifier");

                assertEquals(10, result);
        }

        @Test
        public void GivenUnknownIdentifier_WhenResolveUserId_ThenThrowsException() {
                when(mockUserRepo.findByUsername("missingUser")).thenReturn(Optional.empty());
                when(mockUserRepo.findByEmail("missingUser")).thenReturn(Optional.empty());

                assertThrows(RuntimeException.class,
                                () -> companyService.resolveUserId("missingUser"));
        }

        @Test
        public void GivenNullIdentifier_WhenResolveUserId_ThenThrowsIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.resolveUserId(null));
        }

        @Test
        public void GivenBlankIdentifier_WhenResolveUserId_ThenThrowsIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.resolveUserId("   "));
        }



        /////////////////////////////Tests for getManagerPermissions

        @Test
        public void GivenActiveManagerWithPermissions_WhenGetManagerPermissions_ThenReturnsPermissionList() {
        ProductionCompany company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME,
                CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        User ownerUser = new User(OWNER_ID, "ownerUser", "", "password", 22);
        ownerUser.addFounderAppointment(COMPANY_ID);
        User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);
        targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);
        targetUser.acceptInvitation(COMPANY_ID);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        List<Permission> result = companyService.getManagerPermissions(OWNER_TOKEN, COMPANY_ID, TARGET_USER_ID);

        assertNotNull(result);
        assertEquals(defaultPermissions.size(), result.size());
        assertTrue(result.containsAll(defaultPermissions));
        }

        @Test
        public void GivenInvalidToken_WhenGetManagerPermissions_ThenThrowsException() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> companyService.getManagerPermissions(INVALID_TOKEN, COMPANY_ID, TARGET_USER_ID));
        }

        @Test
        public void GivenUserWithNoPendingOrActiveAppointment_WhenGetManagerPermissions_ThenThrowsException() {
        User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        assertThrows(RuntimeException.class,
                () -> companyService.getManagerPermissions(OWNER_TOKEN, COMPANY_ID, TARGET_USER_ID));
        }

        @Test
        public void GivenPendingButNotAcceptedInvitation_WhenGetManagerPermissions_ThenThrowsException() {
        User targetUser = new User(TARGET_USER_ID, "targetUser", "", "password", 20);
        targetUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, defaultPermissions);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockUserRepo.getUserById(TARGET_USER_ID)).thenReturn(targetUser);

        assertThrows(RuntimeException.class,
                () -> companyService.getManagerPermissions(OWNER_TOKEN, COMPANY_ID, TARGET_USER_ID));
        }
}

