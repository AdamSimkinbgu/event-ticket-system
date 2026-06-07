package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/announcements", layout = AdminLayout.class)
@PageTitle("Announcements · Admin")
@RolesAllowed("ADMIN")
public class AdminAnnouncementsView extends PlaceholderView {
    public AdminAnnouncementsView() {
        super(
            "System announcements",
            "V2-VIEW-NW-01",
            "Bar (@BarMiyara)",
            "Admin broadcasts (II.6.3.2). Sends a Conversation with type=ANNOUNCEMENT, audience=BROADCAST_MEMBERS via MessagingService.announce. Closes #141."
        );
        add(wireCard("New announcement",
            wireForm("Subject (TextField)", "Body (TextArea)", "Audience (Select — all members / specific role)")
        ));
        add(wireActions("Send", "Save draft"));
        add(wireSectionTitle("Past announcements"));
        add(wireGrid("Sent", "Subject", "Audience", "Recipients", "Sender"));
    }
}
