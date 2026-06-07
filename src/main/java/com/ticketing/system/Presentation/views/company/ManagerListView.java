package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/managers", layout = MainLayout.class)
@PageTitle("Managers · Event Ticket Platform")
@PermitAll
public class ManagerListView extends PlaceholderView {
    public ManagerListView() {
        super(
            "Managers",
            "V2-CADMIN-03",
            "Abed Faour",
            "Owner-side roster of company managers. Allows editing permissions and revoking. Closes #128, #129, II.4.11 / II.4.12."
        );
        add(wireSectionTitle("Active managers"));
        add(wireGrid("Name", "Role", "Permissions", "Status", "Actions"));
        add(wireBox("Per row: \"Edit permissions\" → Dialog · \"Revoke\" Button (confirm)"));
        add(wireSectionTitle("Pending invitations"));
        add(wireGrid("Invitee", "Role", "Sent", "Status"));
        add(wireActions("+ Invite manager"));
    }
}
