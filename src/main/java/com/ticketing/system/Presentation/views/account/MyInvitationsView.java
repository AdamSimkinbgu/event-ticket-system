package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Core.Application.dto.AppointmentResponseDTO;
import com.ticketing.system.Core.Application.dto.PendingInvitationDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.session.MockCompanies;
import com.ticketing.system.Presentation.session.MockPermissions;
import com.ticketing.system.Presentation.session.MockSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Route(value = "my-invitations", layout = MainLayout.class)
@PageTitle("Invitations · TicketHub")
@PermitAll
public class MyInvitationsView extends LkPage {

    private final CompanyManagementService companyService;

    public MyInvitationsView(CompanyManagementService companyService) {
        this.companyService = companyService;
        title("Invitations");
        subtitle("Accept a role to manage events for a production company.");
        add(Lk.h2("Pending invitations"));
        add(buildPendingCard());
        add(Lk.h2("History"));
        add(buildHistoryCard());
    }


   private Component buildPendingCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Company",     "company")
            .col("Role",        "role")
            .col("Invited by",  "by")
            .col("Permissions", "perms")
            .col("",            "act", LkGrid.Align.RIGHT);

        String token = MockAuth.token();
        if (token != null) {
            try {
                List<PendingInvitationDTO> invitations = companyService.listPendingInvitations(token);
                if (invitations.isEmpty()) {
                    card.add(Lk.muted("No pending invitations."));
                    return card;
                }
                for (PendingInvitationDTO inv : invitations) {
                    pending(grid, inv);
                }
            } catch (Exception e) {
                card.add(Lk.muted("Could not load invitations: " + e.getMessage()));
                return card;
            }
        } else {
            card.add(Lk.muted("Not signed in."));
            return card;
        }

        grid.build();
        card.add(grid);
        return card;
    }
    private void pending(LkGrid grid, PendingInvitationDTO inv) {
    Map<String, Object> row = new LinkedHashMap<>();
    Span c = new Span();
    c.getElement().setProperty("innerHTML", "<b>" + escape(inv.companyName()) + "</b>");
    row.put("company", c);

    String roleTone = inv.role().equalsIgnoreCase("Owner") ? "primary" : "success";
    row.put("role", new LkBadge(inv.role(), LkBadge.Tone.valueOf(roleTone)).small());
    row.put("by", inv.inviterName());

    String permsText = inv.permissions().isEmpty()
        ? "Full company access"
        : inv.permissions().stream()
              .map(Permission::name)
              .map(n -> n.replace("_", " ").toLowerCase())
              .collect(Collectors.joining(" · "));
    row.put("perms", Lk.muted(permsText));

    LkRow actions = new LkRow().gap(6).noWrap();
    actions.add(
        new LkBtn("Accept").variant(LkBtn.Variant.primary).size(LkBtn.Size.s)
            .onClick(e -> respond(inv, true)),
        new LkBtn("Reject").variant(LkBtn.Variant.tertiary).size(LkBtn.Size.s)
            .onClick(e -> respond(inv, false))
    );
    row.put("act", actions);
    grid.row(row);
}

    private void respond(PendingInvitationDTO inv, boolean accept) {
        String token = MockAuth.token();
        if (token == null) {
            Toasts.failure("Session token missing — please log in again.");
            return;
        }
        try {
            companyService.respondToAppointment(
                token, new AppointmentResponseDTO(inv.companyId(), accept));
            if (accept) {
                // Seed the UI session with exactly the permissions the owner granted.
                // Without this, Capabilities.forCurrentUser() falls back to DEFAULT_GRANTS.
                String cid = String.valueOf(inv.companyId());
                String uiRole = inv.role().equalsIgnoreCase("Owner") ? "Co-owner" : "Manager";
                MockCompanies.add(new MockCompanies.Company(
                     cid, inv.companyName(), "", "", uiRole, "Active", 0, 0));
                MockSession.setCurrentCompany(cid);
                if ("Manager".equals(uiRole)) {
                    MockPermissions.setAll(
                        cid, MockPermissions.fromDomainPermissions(inv.permissions()));
                }
                Toasts.success("Accepted — you are now a " + uiRole.toLowerCase() + " at " + inv.companyName() + ".");          
            } else {
                Toasts.warn("Invitation from " + inv.companyName() + " rejected.");
            }
            UI.getCurrent().getPage().reload();
        } catch (Exception e) {
            Toasts.failure("Could not respond to invitation: " + e.getMessage());
        }
    }
    private Component buildHistoryCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Company", "company")
            .col("Role",    "role")
            .col("Outcome", "outcome")
            .col("Date",    "date");

        history(grid, "Mashina Productions", "Manager", "Accepted", LkStatusDot.Tone.ok,    "12 Mar 2026");
        history(grid, "Teddy Events",        "Manager", "Rejected", LkStatusDot.Tone.muted, "2 Feb 2026");

        grid.build();
        card.add(grid);
        return card;
    }

    private void history(LkGrid grid, String company, String role, String outcome, LkStatusDot.Tone tone, String date) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span c = new Span();
        c.getElement().setProperty("innerHTML", "<b>" + escape(company) + "</b>");
        row.put("company", c);
        row.put("role", role);
        row.put("outcome", new LkStatusDot(tone, outcome));
        row.put("date", date);
        grid.row(row);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
