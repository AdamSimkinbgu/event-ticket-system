package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Admin workspace · Event Ticket Platform")
@RolesAllowed("ADMIN")
public class AdminDashboardView extends PlaceholderView {
    public AdminDashboardView() {
        super(
            "Admin workspace",
            "V2-VIEW-NW-00",
            "Bar (@BarMiyara)",
            "Hub for system-administrator activity. Lands here on /admin. Every admin sub-view is reachable as a clickable card below, plus from the left drawer."
        );
        add(wireCardGrid(
            wireNavCard("📊", "Global Purchase History",
                "Every order across the platform with date / company / event / status filters and revenue aggregations.",
                GlobalHistoryView.class),
            wireNavCard("🌳", "Organizational Tree",
                "Per-company appointment hierarchy — founder → owners → managers with their granted permissions.",
                OrganizationalTreeView.class),
            wireNavCard("📢", "Announcements",
                "Broadcast a Conversation of type ANNOUNCEMENT to all members or a specific role.",
                AdminAnnouncementsView.class),
            wireNavCard("📬", "Complaint Queue",
                "Handle member complaints opened via Conversation type COMPLAINT. Reply, mark resolved, filter by status.",
                AdminComplaintQueueView.class)
        ));
    }
}
