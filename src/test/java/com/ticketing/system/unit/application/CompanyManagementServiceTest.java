package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class CompanyManagementServiceTest {
    @Test @Disabled("UC-18: register creates company + initial Founder/Owner appointment")
    void givenAuthenticatedMember_whenRegisterCompany_thenCompanyAndFounderCreated() {}

    @Test @Disabled("UC-21: setCompanyPolicies stores company-wide policies")
    void givenOwner_whenSetCompanyPolicies_thenStored() {}

    @Test @Disabled("UC-23: appointOwner creates PENDING appointment")
    void givenOwner_whenAppointCoOwner_thenPending() {}

    @Test @Disabled("UC-23: respond accepts and activates")
    void givenPendingAppointment_whenAccept_thenActive() {}

    @Test @Disabled("UC-24: appointManager with selected permissions")
    void givenOwner_whenAppointManagerWithPermissions_thenPending() {}

    @Test @Disabled("UC-24: editManagerPermissions only by original appointer")
    void givenManager_whenEditByDifferentOwner_thenRejected() {}

    @Test @Disabled("UC-24: revokeManager flips status to REVOKED")
    void givenActiveManager_whenRevoke_thenRevoked() {}

    @Test @Disabled("UC-22: viewSalesHistory returns flat list of company sales")
    void givenOwner_whenViewSalesHistory_thenFlatList() {}

    @Test @Disabled("UC-25: viewOrganizationalTree returns nested tree of ACTIVE appointments")
    void givenOwner_whenViewOrgTree_thenNestedTree() {}
}
