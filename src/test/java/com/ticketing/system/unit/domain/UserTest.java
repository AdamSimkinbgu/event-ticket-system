package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Unit tests for the User aggregate.
class UserTest {

    @Test
    @Disabled("V1: acceptInvitation moves invitation to appointment (UC-23/UC-24)")
    void givenPendingInvitation_whenAccept_thenAppointmentAdded() {}

    @Test
    @Disabled("V1: rejectInvitation removes invitation, no appointment")
    void givenPendingInvitation_whenReject_thenInvitationRemoved() {}

    @Test
    @Disabled("V1: removeCompanyAppointment revokes manager role")
    void givenAppointment_whenRemove_thenRoleRevoked() {}

    @Test
    @Disabled("V1: ModifyManagerPermissions updates permission set (UC-24)")
    void givenAppointment_whenModifyPermissions_thenUpdated() {}
}
