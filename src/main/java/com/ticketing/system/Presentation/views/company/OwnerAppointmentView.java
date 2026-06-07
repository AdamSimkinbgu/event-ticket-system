package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/owners/appoint", layout = MainLayout.class)
@PageTitle("Appoint co-owner · Event Ticket Platform")
@PermitAll
public class OwnerAppointmentView extends PlaceholderView {
    public OwnerAppointmentView() {
        super(
            "Appoint co-owner",
            "V2-APPT-01",
            "Bentzion Hadad",
            "UC-23 / II.4.8.x — owner appoints another user as co-owner. Calls CompanyManagementService.appointCoOwner. Cycle prevention enforced server-side (V2-APPT-02). Closes #33, #123–125."
        );
        add(wireCard("Appoint co-owner",
            wireForm("Invitee username or email (required)", "Scope: this company (read-only)")
        ));
        add(wireBox("Validation: cannot create ownership cycle · cannot re-appoint founder"));
        add(wireActions("Send invitation", "Cancel"));
        add(wireBox("Invitee accepts via /support inbox; OwnerAppointment row created on accept"));
    }
}
