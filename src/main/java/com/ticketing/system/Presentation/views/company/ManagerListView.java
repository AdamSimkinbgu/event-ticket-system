package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.Map;

@Route(value = "owner/managers", layout = WorkspaceLayout.class)
@PageTitle("Managers · TicketHub")
@PermitAll
@RequireCapability(Capability.APPOINT_MANAGER)
public class ManagerListView extends LkPage {

    public ManagerListView() {
        title("Managers");
        subtitle("Roster and permissions for your production company.");
        actions(new LkBtn("Invite manager")
            .variant(LkBtn.Variant.primary)
            .icon(new LkIcon("plus", 15))
            .onClick(e -> UI.getCurrent().navigate(ManagerInvitationView.class)));

        add(Lk.h2("Active managers"));
        add(buildActiveCard());
        add(Lk.h2("Pending invitations"));
        add(buildPendingCard());
    }

    private Component buildActiveCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Name",        "name")
            .col("Role",        "role")
            .col("Permissions", "perms")
            .col("Status",      "status")
            .col("",            "act", LkGrid.Align.RIGHT);

        activeRow(grid, "Carol Levy",    "Manager", "Manage events · Inquiries");
        activeRow(grid, "David Cohen",   "Manager", "View sales · Edit policies");
        activeRow(grid, "Rina Shapiro",  "Manager", "Manage events");
        grid.build();
        card.add(grid);
        return card;
    }

    private void activeRow(LkGrid grid, String name, String role, String perms) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span n = new Span();
        n.getElement().setProperty("innerHTML", "<b>" + name + "</b>");
        row.put("name", n);
        row.put("role", new LkBadge(role, LkBadge.Tone.success).small());
        Span p = Lk.muted(perms);
        p.getStyle().set("font-size", "13px");
        row.put("perms", p);
        row.put("status", new LkStatusDot(LkStatusDot.Tone.ok, "Active"));
        LkRow actions = new LkRow().gap(4).noWrap();
        if (Capabilities.has(Capability.EDIT_MANAGER_PERMISSIONS)) {
            LkIconBtn edit = new LkIconBtn(new LkIcon("edit", 15), "Edit permissions");
            edit.addClickListener(e -> Toasts.warn("Edit-permissions dialog (V2-CADMIN-03)."));
            actions.add(edit);
        }
        if (Capabilities.has(Capability.REVOKE_MANAGER)) {
            LkIconBtn revoke = new LkIconBtn(new LkIcon("trash", 15), "Revoke");
            revoke.addClickListener(e -> Toasts.warn("Confirm-revoke dialog (V2-CADMIN-03)."));
            actions.add(revoke);
        }
        row.put("act", actions);
        grid.row(row);
    }

    private Component buildPendingCard() {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Invitee", "invitee")
            .col("Role",    "role")
            .col("Sent",    "sent")
            .col("Status",  "status");

        pendingRow(grid, "yossi.mizrahi", "Manager", "3 days ago");
        pendingRow(grid, "lior.adler",    "Manager", "1 week ago");
        grid.build();
        card.add(grid);
        return card;
    }

    private void pendingRow(LkGrid grid, String invitee, String role, String sent) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span inv = Lk.mono(invitee);
        row.put("invitee", inv);
        row.put("role", role);
        row.put("sent", sent);
        row.put("status", new LkStatusDot(LkStatusDot.Tone.warn, "Pending"));
        grid.row(row);
    }
}
