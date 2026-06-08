package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.company.CompanyAppointment;
import com.ticketing.system.Core.Domain.users.CompanyRole;
import com.ticketing.system.Core.Domain.users.ManagementInvitation;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.support.BaseDomainTest;

public class UserTest extends BaseDomainTest {

        private final int USER_ID = 2;
        private final int OWNER_ID = 1;
        private final int COMPANY_ID = 100;
        private final int OTHER_COMPANY_ID = 200;

        private User user;
        private User owner;
        private List<Permission> defaultPermissions;

        @BeforeEach
        public void setUp() {

                user = track(new User(USER_ID, "targetUser", "target@example.com", "password"));
                owner = track(new User(OWNER_ID, "owner", "owner@example.com", "password"));

                defaultPermissions = new ArrayList<>();
                defaultPermissions.add(Permission.APPOINT_MANAGER);
                defaultPermissions.add(Permission.CONFIGURE_VENUE);
                defaultPermissions.add(Permission.MANAGE_INVENTORY);
                owner.addFounderAppointment(COMPANY_ID);
        }

        @Test
        public void GivenUser_WhenInvitedToCompanyAppointment_ThenUserHasOneInvitation() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                assertEquals(1, user.getAllCompanyAppointments().size());
        }

        @Test
        public void GivenPendingInvitation_WhenAcceptInvitation_ThenInvitationReturned() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                CompanyAppointment appontment = user.acceptInvitation(COMPANY_ID);

                assertEquals(COMPANY_ID, appontment.getCompanyId());
        }

        @Test
        public void GivenPendingInvitation_WhenAcceptInvitation_ThenInvitationRemoved() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(COMPANY_ID);

                assertEquals(null, user.getPendingCompanyAppointments(COMPANY_ID));
        }

        @Test
        public void GivenPendingInvitation_WhenAcceptInvitation_ThenCompanyAppointmentCreated() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(COMPANY_ID);

                assertNotEquals(null, user.getActiveCompanyAppointments(COMPANY_ID));
        }

        @Test
        public void GivenPendingInvitation_WhenAcceptInvitation_ThenAppointmentHasCorrectCompanyId() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(COMPANY_ID);

                CompanyAppointment appointment = user.getAllCompanyAppointments().get(0);

                assertEquals(COMPANY_ID, appointment.getCompanyId());
        }

        @Test
        public void GivenPendingInvitation_WhenRejectInvitation_ThenInvitationRemoved() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.rejectInvitation(COMPANY_ID);

                assertEquals(null, user.getPendingCompanyAppointments(COMPANY_ID));
        }

        @Test
        public void GivenPendingInvitation_WhenRejectInvitation_ThenNoCompanyAppointmentCreated() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.rejectInvitation(COMPANY_ID);

                assertEquals(null, user.getActiveCompanyAppointments(COMPANY_ID));
        }

        @Test
        public void GivenNoInvitation_WhenAcceptInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> user.acceptInvitation(COMPANY_ID));
        }

        @Test
        public void GivenNoInvitation_WhenRejectInvitation_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> user.rejectInvitation(COMPANY_ID));
        }

        @Test
        public void GivenInvitationForOtherCompany_WhenAcceptInvitation_ThenThrowException() {
                user.receiveManagerAppointment(
                                OTHER_COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                assertThrows(RuntimeException.class, () -> user.acceptInvitation(COMPANY_ID));
        }

        @Test
        public void GivenInvitationForOtherCompany_WhenRejectInvitation_ThenThrowException() {
                user.receiveManagerAppointment(
                                OTHER_COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                assertThrows(RuntimeException.class, () -> user.rejectInvitation(COMPANY_ID));
        }

        @Test
        public void GivenAcceptedAppointment_WhenrevokeManagerAppointment_ThenAppointmentRemoved() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(COMPANY_ID);

                user.revokeManagerAppointment(COMPANY_ID, OWNER_ID);

                assertEquals(null, user.getActiveCompanyAppointments(COMPANY_ID));
        }

        @Test
        public void GivenNoAppointment_WhenrevokeManagerAppointment_ThenThrowException() {
                assertThrows(RuntimeException.class, () -> user.revokeManagerAppointment(COMPANY_ID, OWNER_ID));
        }

        @Test
        public void GivenAppointmentForOtherCompany_WhenrevokeManagerAppointment_ThenThrowException() {
                user.receiveManagerAppointment(
                                OTHER_COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(OTHER_COMPANY_ID);

                assertThrows(RuntimeException.class, () -> user.revokeManagerAppointment(COMPANY_ID, OWNER_ID));
        }

        @Test
        public void GivenAcceptedAppointment_WhenModifyManagerPermissions_ThenPermissionsUpdated() {
                user.receiveManagerAppointment(
                                COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(COMPANY_ID);

                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.EDIT_POLICIES);

                user.ModifyManagerPermissions(
                                COMPANY_ID,
                                OWNER_ID,
                                newPermissions);

                CompanyAppointment appointment = user.getActiveCompanyAppointments(COMPANY_ID);

                assertEquals(newPermissions, appointment.getPermissions().stream().toList());
        }

        @Test
        public void GivenNoAppointment_WhenModifyManagerPermissions_ThenThrowException() {
                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.EDIT_POLICIES);

                assertThrows(RuntimeException.class, () -> user.ModifyManagerPermissions(
                                COMPANY_ID,
                                USER_ID,
                                newPermissions));
        }

        @Test
        public void GivenAppointmentForOtherCompany_WhenModifyManagerPermissions_ThenThrowException() {
                user.receiveManagerAppointment(
                                OTHER_COMPANY_ID,
                                OWNER_ID,
                                defaultPermissions);

                user.acceptInvitation(OTHER_COMPANY_ID);

                List<Permission> newPermissions = new ArrayList<>();
                newPermissions.add(Permission.EDIT_POLICIES);

                assertThrows(RuntimeException.class, () -> user.ModifyManagerPermissions(
                                COMPANY_ID,
                                USER_ID,
                                newPermissions));
        }

}