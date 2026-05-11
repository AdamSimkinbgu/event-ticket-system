package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Unit tests for the ProductionCompany aggregate.
class ProductionCompanyTest {

    @Test
    @Disabled("V1: validateManagerInvitation rejects when target is owner (UC-24)")
    void givenOwnerAsTarget_whenValidate_thenThrows() {}

    @Test
    @Disabled("V1: validateManagerInvitation rejects when target already manager (UC-24)")
    void givenAlreadyManager_whenValidate_thenThrows() {}

    @Test
    @Disabled("V1: acceptManagerInvitation moves pending -> active (UC-24)")
    void givenPendingInvitation_whenAccept_thenActive() {}

    @Test
    @Disabled("V1: appointment-tree cycle prevention (II.4.8.3 — UC-23)")
    void givenCyclicAppointment_whenAppoint_thenThrows() {}

    @Test
    @Disabled("V1: only the appointer can revoke (UC-24)")
    void givenManager_whenRevokeByOriginalOwner_thenSuccess() {}
}
