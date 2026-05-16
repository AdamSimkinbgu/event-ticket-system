package com.ticketing.system.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CompanyAcceptanceTest {

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
    @Test @Disabled("UC-24 main: appoint Manager with selected permissions")
    void GivenOwner_WhenAppointManager_ThenWithPermissions() {}
    @Test @Disabled("UC-24 main: edit Manager permissions by original appointer")
    void GivenAppointer_WhenEditPermissions_ThenUpdated() {}
    @Test @Disabled("UC-24 negative: edit by different Owner rejected")
    void GivenDifferentOwner_WhenEditPermissions_ThenRejected() {}
    @Test @Disabled("UC-24 main: revoke Manager flips to REVOKED")
    void GivenAppointer_WhenRevokeManager_ThenRevoked() {}

    // UC-25
    @Test @Disabled("UC-25 main: Owner views organizational tree (ACTIVE only)")
    void GivenOwner_WhenViewOrgTree_ThenNestedActiveOnly() {}
}
