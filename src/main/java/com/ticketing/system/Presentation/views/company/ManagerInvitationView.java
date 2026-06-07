package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/managers/invite", layout = MainLayout.class)
@PageTitle("Invite manager · Event Ticket Platform")
@PermitAll
public class ManagerInvitationView extends PlaceholderView {
    public ManagerInvitationView() {
        super(
            "Invite a manager",
            "V2-CADMIN-02",
            "Abed Faour",
            "Owner-side flow for II.4.7.x (Appoint Manager). Calls CompanyManagementService.inviteManager. Creates a ManagementInvitation row the invitee accepts via their account. Closes #120, #121, #122."
        );
        add(wireCard("Invitee",
            wireForm("Username or email (required)")
        ));
        add(wireCard("Permissions",
            wireBox("☐ Manage events"),
            wireBox("☐ View sales history"),
            wireBox("☐ Edit purchase policies"),
            wireBox("☐ Respond to inquiries")
        ));
        add(wireActions("Send invitation", "Cancel"));
    }
}
