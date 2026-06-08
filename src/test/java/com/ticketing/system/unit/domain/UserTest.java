package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.users.CompanyAppointment;
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
    private List<Permission> defaultPermissions;

    @BeforeEach
    public void setUp() {
        user = track(new User(USER_ID, "targetUser", "target@example.com", "password",20));

        defaultPermissions = new ArrayList<>();
        defaultPermissions.add(Permission.APPOINT_MANAGER);
        defaultPermissions.add(Permission.CONFIGURE_VENUE);
        defaultPermissions.add(Permission.MANAGE_INVENTORY);
    }

    @Test
    public void GivenUser_WhenInvitedToCompanyAppointment_ThenUserHasOneInvitation() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        assertEquals(1, user.getManagementInvitations().size());
    }

    @Test
    public void GivenPendingInvitation_WhenAcceptInvitation_ThenInvitationReturned() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        ManagementInvitation invitation = user.acceptInvitation(COMPANY_ID);

        assertEquals(COMPANY_ID, invitation.getCompanyId());
    }

    @Test
    public void GivenPendingInvitation_WhenAcceptInvitation_ThenInvitationRemoved() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        assertTrue(user.getManagementInvitations().isEmpty());
    }

    @Test
    public void GivenPendingInvitation_WhenAcceptInvitation_ThenCompanyAppointmentCreated() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        assertEquals(1, user.getCompanyAppointments().size());
    }

    @Test
    public void GivenPendingInvitation_WhenAcceptInvitation_ThenAppointmentHasCorrectCompanyId() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        CompanyAppointment appointment = user.getCompanyAppointments().get(0);

        assertEquals(COMPANY_ID, appointment.getCompanyId());
    }

    @Test
    public void GivenPendingInvitation_WhenRejectInvitation_ThenInvitationRemoved() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.rejectInvitation(COMPANY_ID);

        assertTrue(user.getManagementInvitations().isEmpty());
    }

    @Test
    public void GivenPendingInvitation_WhenRejectInvitation_ThenNoCompanyAppointmentCreated() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.rejectInvitation(COMPANY_ID);

        assertTrue(user.getCompanyAppointments().isEmpty());
    }

    @Test
    public void GivenNoInvitation_WhenAcceptInvitation_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                user.acceptInvitation(COMPANY_ID)
        );
    }

    @Test
    public void GivenNoInvitation_WhenRejectInvitation_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                user.rejectInvitation(COMPANY_ID)
        );
    }

    @Test
    public void GivenInvitationForOtherCompany_WhenAcceptInvitation_ThenThrowException() {
        user.InvitetoCompanyAppointment(
                OTHER_COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        assertThrows(RuntimeException.class, () ->
                user.acceptInvitation(COMPANY_ID)
        );
    }

    @Test
    public void GivenInvitationForOtherCompany_WhenRejectInvitation_ThenThrowException() {
        user.InvitetoCompanyAppointment(
                OTHER_COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        assertThrows(RuntimeException.class, () ->
                user.rejectInvitation(COMPANY_ID)
        );
    }

    @Test
    public void GivenAcceptedAppointment_WhenRemoveCompanyAppointment_ThenAppointmentRemoved() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        user.removeCompanyAppointment(COMPANY_ID);

        assertTrue(user.getCompanyAppointments().isEmpty());
    }

    @Test
    public void GivenNoAppointment_WhenRemoveCompanyAppointment_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                user.removeCompanyAppointment(COMPANY_ID)
        );
    }

    @Test
    public void GivenAppointmentForOtherCompany_WhenRemoveCompanyAppointment_ThenThrowException() {
        user.InvitetoCompanyAppointment(
                OTHER_COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(OTHER_COMPANY_ID);

        assertThrows(RuntimeException.class, () ->
                user.removeCompanyAppointment(COMPANY_ID)
        );
    }

    @Test
    public void GivenAcceptedAppointment_WhenModifyManagerPermissions_ThenPermissionsUpdated() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.EDIT_POLICIES);

        user.ModifyManagerPermissions(
                COMPANY_ID,
                USER_ID,
                newPermissions
        );

        CompanyAppointment appointment = user.getCompanyAppointments().get(0);

        assertEquals(newPermissions, appointment.getPermissions());
    }

    @Test
    public void GivenNoAppointment_WhenModifyManagerPermissions_ThenThrowException() {
        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.EDIT_POLICIES);

        assertThrows(RuntimeException.class, () ->
                user.ModifyManagerPermissions(
                        COMPANY_ID,
                        USER_ID,
                        newPermissions
                )
        );
    }

    @Test
    public void GivenAppointmentForOtherCompany_WhenModifyManagerPermissions_ThenThrowException() {
        user.InvitetoCompanyAppointment(
                OTHER_COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(OTHER_COMPANY_ID);

        List<Permission> newPermissions = new ArrayList<>();
        newPermissions.add(Permission.EDIT_POLICIES);

        assertThrows(RuntimeException.class, () ->
                user.ModifyManagerPermissions(
                        COMPANY_ID,
                        USER_ID,
                        newPermissions
                )
        );
    }

    @Test
    public void GivenPendingInvitation_WhenAcceptInvitation_ThenMemberProfileIdUpdated() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        assertEquals(COMPANY_ID, user.getMemberProfile().getCompanyId());
    }

    @Test
    public void GivenPendingInvitation_WhenAcceptInvitation_ThenMemberProfileRoleIsManager() {
        user.InvitetoCompanyAppointment(
                COMPANY_ID,
                OWNER_ID,
                defaultPermissions
        );

        user.acceptInvitation(COMPANY_ID);

        assertEquals(CompanyRole.Manager, user.getMemberProfile().getCompanyRole());
    }

    
}