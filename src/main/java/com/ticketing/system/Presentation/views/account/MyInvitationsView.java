package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "my-invitations", layout = MainLayout.class)
@PageTitle("Invitations · Event Ticket Platform")
@PermitAll
public class MyInvitationsView extends PlaceholderView {
    public MyInvitationsView() {
        super(
            "Invitations",
            "V2-INV-01",
            "Bentzion Hadad",
            "Receiver side of the manager + co-owner appointment flows (II.4.7.x / II.4.8.x). Lists pending invitations; accept creates the role, reject closes the invitation. Backed by CompanyManagementService.findInvitationsForUser."
        );
        add(wireSectionTitle("Pending invitations"));
        add(wireGrid("Company", "Role", "Invited by", "Permissions", "Received", "Actions"));
        add(wireBox("Per row: \"Accept\" (primary) · \"Reject\" (secondary) · \"View company profile\" (link)"));
        add(wireSectionTitle("History"));
        add(wireGrid("Company", "Role", "Outcome", "Date"));
        add(wireBox("History shows accepted (turned into ManagerAppointment / OwnerAppointment) and rejected invitations"));
    }
}
