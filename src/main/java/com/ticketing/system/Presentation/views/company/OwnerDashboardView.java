package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Core.Application.dto.UserCompanyDTO;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkStat;
import com.ticketing.system.Presentation.components.kit.LkTile;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.presenters.company.MyCompaniesPresenter;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.views.admin.CompanySalesView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner", layout = WorkspaceLayout.class)
@PageTitle("Workspace · TicketHub")
@PermitAll
@RequireCapability(Capability.OWNER_WORKSPACE)
public class OwnerDashboardView extends LkPage {

    public OwnerDashboardView(MyCompaniesPresenter membershipPresenter) {
        UserCompanyDTO company = membershipPresenter.currentCompany();

        title("Workspace");
        subtitle(company.name() + "  ·  you are the " + company.role());
        actions(new LkBtn("New event")
            .variant(LkBtn.Variant.primary)
            .icon(new LkIcon("plus", 15))
            .onClick(e -> UI.getCurrent().navigate(CompanyEventListView.class)));

        add(buildStats(company));
        add(Lk.h2("Manage"));
        add(buildTiles());
    }

    private Component buildStats(UserCompanyDTO company) {
        Div stats = new Div();
        stats.addClassName("ow-stats");
        boolean fresh = company.activeEvents() == 0;
        stats.add(
            new LkStat("Live events",           String.valueOf(company.activeEvents())),
            fresh ? new LkStat("Tickets sold · 30d", "0") :
                    new LkStat("Tickets sold · 30d", "14,208").delta("▲ 12%", LkStat.Tone.up),
            fresh ? new LkStat("Revenue · 30d", "$0") :
                    new LkStat("Revenue · 30d", "$1.92M").delta("▲ 8%", LkStat.Tone.up),
            fresh ? new LkStat("Open inquiries", "0") :
                    new LkStat("Open inquiries", "3").delta("needs reply", LkStat.Tone.warn)
        );
        return stats;
    }

    private Component buildTiles() {
        Div tiles = new Div();
        tiles.addClassName("ow-tiles");

        if (Capabilities.has(Capability.VIEW_COMPANY_EVENTS))
            tiles.add(tile("calendar", "My Events",
                "Edit metadata, venue map, policies, or cancel an event.",
                CompanyEventListView.class));

        if (Capabilities.has(Capability.RESPOND_INQUIRIES))
            tiles.add(tile("comment", "Member Inquiries",
                "Respond to questions about your events and mark resolved.",
                CompanyInquiryInboxView.class));

        if (Capabilities.has(Capability.VIEW_COMPANY_SALES))
            tiles.add(tile("chart", "Sales History",
                "Per-company sales with date / event filters. Immutable receipts.",
                CompanySalesView.class));

        if (Capabilities.has(Capability.APPOINT_MANAGER))
            tiles.add(tile("users", "Managers",
                "Active managers + pending invites. Edit permissions or revoke.",
                ManagerListView.class));

        if (Capabilities.has(Capability.APPOINT_CO_OWNER))
            tiles.add(tile("crown", "Appoint Co-owner",
                "Invite another member as co-owner. Cycle-prevention enforced.",
                OwnerAppointmentView.class));

        if (Capabilities.has(Capability.EDIT_PURCHASE_POLICIES))
            tiles.add(tile("policy", "Purchase Policies",
                "Visual AND/OR builder for company- or event-level rules.",
                PurchasePolicyEditorView.class));

        if (Capabilities.has(Capability.REGISTER_COMPANY))
            tiles.add(tile("briefcase", "Register New Company",
                "Found another production company. You become the founder.",
                CompanyRegistrationView.class));

        return tiles;
    }

    private LkTile tile(String iconName, String title, String desc, Class<? extends Component> target) {
        LkTile t = new LkTile(new LkIcon(iconName, 26), title, desc);
        t.addClickListener(e -> UI.getCurrent().navigate(target));
        return t;
    }
}
