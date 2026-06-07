package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.ticketing.system.Presentation.views.admin.CompanySalesView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner", layout = AdminLayout.class)
@PageTitle("Owner workspace · Event Ticket Platform")
@PermitAll
public class OwnerDashboardView extends PlaceholderView {
    public OwnerDashboardView() {
        super(
            "Owner workspace",
            "V2-CADMIN-00",
            "Abed Faour",
            "Hub for production-company owners and managers. Lands here on /owner. Every owner-side view is reachable as a clickable card below, plus from the left drawer."
        );
        add(wireCardGrid(
            wireNavCard("📅", "My Events",
                "All events under the selected company. Edit metadata, manage venue / zones, configure policies, or cancel.",
                CompanyEventListView.class),
            wireNavCard("💬", "Member Inquiries",
                "Conversations opened by members about your events. Respond and mark resolved (II.4.4).",
                CompanyInquiryInboxView.class),
            wireNavCard("💼", "Sales History",
                "Per-company sales with date / event filters and revenue summary (UC-22). Immutable receipts.",
                CompanySalesView.class),
            wireNavCard("👥", "Managers",
                "Active managers + pending invitations. Edit granted permissions or revoke (II.4.7 / 4.11 / 4.12).",
                ManagerListView.class),
            wireNavCard("👑", "Appoint Co-owner",
                "Invite another user as company co-owner (UC-23, II.4.8.x). Cycle prevention enforced server-side.",
                OwnerAppointmentView.class),
            wireNavCard("📜", "Purchase Policies",
                "Visual AND/OR tree builder for company- or event-level purchase policies (II.4.3.1, Tier C).",
                PurchasePolicyEditorView.class),
            wireNavCard("🏢", "Register New Company",
                "Found a new production company (UC-18, II.3.2.x). You become the immutable founder.",
                CompanyRegistrationView.class)
        ));
    }
}
