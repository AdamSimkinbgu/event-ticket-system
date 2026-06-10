package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.session.MockCompanies;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "my-companies", layout = MainLayout.class)
@PageTitle("My companies · TicketHub")
@PermitAll
public class MyCompaniesView extends LkPage {

    public MyCompaniesView() {
        title("My companies");
        subtitle("Companies where you are a founder, owner, or manager.");
        actions(new LkBtn("Register a company")
            .variant(LkBtn.Variant.primary)
            .icon(new LkIcon("plus", 15))
            .onClick(e -> UI.getCurrent().navigate(CompanyRegistrationView.class)));

        List<MockCompanies.Company> companies = MockCompanies.forCurrentUser();
        if (companies.isEmpty()) {
            add(buildEmptyState());
        } else {
            add(buildGridCard(companies));
        }
    }

    private Component buildEmptyState() {
        Div empty = new Div();
        empty.getStyle()
            .set("background", "#fff")
            .set("border", "1px dashed var(--border-strong)")
            .set("border-radius", "14px")
            .set("padding", "48px 32px")
            .set("text-align", "center");

        Div iconWrap = new Div();
        iconWrap.getStyle()
            .set("width", "60px").set("height", "60px")
            .set("background", "rgba(26,84,144,0.08)")
            .set("border-radius", "12px")
            .set("color", "var(--primary)")
            .set("display", "flex").set("align-items", "center").set("justify-content", "center")
            .set("margin", "0 auto 16px");
        iconWrap.add(new LkIcon("building", 28));

        Span title = new Span("You haven't joined any companies yet");
        title.getStyle()
            .set("font-size", "1.1rem").set("font-weight", "800").set("color", "#0f172a")
            .set("display", "block");

        Span body = new Span("Register your first production company to start selling tickets, "
            + "manage events, and invite managers. You'll be its immutable founder.");
        body.getStyle()
            .set("font-size", "0.92rem").set("color", "var(--muted)")
            .set("max-width", "440px").set("line-height", "1.55")
            .set("display", "block").set("margin", "8px auto 18px");

        empty.add(iconWrap, title, body,
            new LkBtn("Register your first company")
                .variant(LkBtn.Variant.primary)
                .icon(new LkIcon("plus", 15))
                .onClick(e -> UI.getCurrent().navigate(CompanyRegistrationView.class)));
        return empty;
    }

    private Component buildGridCard(List<MockCompanies.Company> companies) {
        LkCard card = new LkCard().pad(0);
        LkGrid grid = new LkGrid()
            .col("Company",       "company")
            .col("Role",          "role")
            .col("Status",        "status")
            .col("Members",       "members", LkGrid.Align.RIGHT)
            .col("Active events", "events",  LkGrid.Align.RIGHT)
            .col("",              "act",     LkGrid.Align.RIGHT);

        for (MockCompanies.Company c : companies) {
            Map<String, Object> row = new LinkedHashMap<>();
            Span name = new Span();
            name.getElement().setProperty("innerHTML", "<b>" + escape(c.name()) + "</b>");
            row.put("company", name);
            row.put("role", new LkBadge(c.role(), toneFor(c.role())).small());
            row.put("status", new LkStatusDot(LkStatusDot.Tone.ok, c.status()));
            row.put("members", c.members());
            row.put("events", c.activeEvents());

            LkRow actions = new LkRow().gap(6).noWrap();
            actions.add(
                new LkBtn("Open").variant(LkBtn.Variant.secondary).size(LkBtn.Size.s)
                    .onClick(e -> UI.getCurrent().navigate(OwnerDashboardView.class)),
                new LkBtn("Events").variant(LkBtn.Variant.tertiary).size(LkBtn.Size.s)
                    .onClick(e -> UI.getCurrent().navigate(CompanyEventListView.class))
            );
            row.put("act", actions);
            grid.row(row);
        }
        grid.build();
        card.add(grid);
        return card;
    }

    private static LkBadge.Tone toneFor(String role) {
        return switch (role) {
            case "Founder"  -> LkBadge.Tone.warning;
            case "Co-owner" -> LkBadge.Tone.primary;
            default         -> LkBadge.Tone.success;
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
