package com.ticketing.system.Presentation.views.account;

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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.Map;

@Route(value = "my-invitations", layout = MainLayout.class)
@PageTitle("Invitations · TicketHub")
@PermitAll
public class MyInvitationsView extends LkPage {

    public MyInvitationsView() {
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
            .col("Received",    "recv")
            .col("",            "act",  LkGrid.Align.RIGHT);

        pending(grid, "Live Nation Israel", "Manager",  "success", "Bob Mizrahi", "Manage events · Respond to inquiries", "2 days ago");
        pending(grid, "Zappa Group",        "Co-owner", "primary", "Dana Peretz", "Full company access",                  "5 days ago");

        grid.build();
        card.add(grid);
        return card;
    }

    private void pending(LkGrid grid, String company, String role, String roleTone, String by, String perms, String recv) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span c = new Span();
        c.getElement().setProperty("innerHTML", "<b>" + escape(company) + "</b>");
        row.put("company", c);
        row.put("role", new LkBadge(role, LkBadge.Tone.valueOf(roleTone)).small());
        row.put("by", by);
        row.put("perms", Lk.muted(perms));
        row.put("recv", recv);

        LkRow actions = new LkRow().gap(6).noWrap();
        actions.add(
            new LkBtn("Accept").variant(LkBtn.Variant.primary).size(LkBtn.Size.s)
                .onClick(e -> Toasts.success("Accepted — you are now a " + role.toLowerCase() + " at " + company + ".")),
            new LkBtn("Reject").variant(LkBtn.Variant.tertiary).size(LkBtn.Size.s)
                .onClick(e -> Toasts.warn("Invitation from " + company + " rejected."))
        );
        row.put("act", actions);
        grid.row(row);
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
