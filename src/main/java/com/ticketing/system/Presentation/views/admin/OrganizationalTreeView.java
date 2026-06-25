package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.components.admin.OrgTreeRenderer;
import com.ticketing.system.Presentation.components.admin.OrgTreeRenderer.Node;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.List;

@Route(value = "admin/org-tree", layout = PlatformAdminLayout.class)
@PageTitle("Organizational Tree · Admin")
@PermitAll
@RequireCapability(Capability.VIEW_ORG_TREES)
public class OrganizationalTreeView extends LkPage {

    public OrganizationalTreeView() {
        title("Organizational Tree");
        subtitle("Founder → owners → managers hierarchy for the selected company, with audit lines.");

        add(buildFilters());
        add(buildTreeCard());
        add(buildLegendCard());
    }

    private Component buildFilters() {
        LkRow row = new LkRow().gap(8);
        row.add(
            new LkFilterChip("Company", List.of("Live Nation Israel", "Coca-Cola Arena", "Shuni Productions"),
                false, List.of("Live Nation Israel")),
            new LkFilterChip("Roles",   List.of("Founder", "Owners", "Managers"), true, List.of("Founder", "Owners", "Managers"))
        );
        return row;
    }

    private Component buildTreeCard() {
        LkCard card = new LkCard("Live Nation Israel — Appointment Hierarchy").pad(20);

        Node carol = new Node("C", "Carol Levy", "Manager", "manager", "Appointed by Bob · Manage events, view sales");
        Node dave  = new Node("D", "Dave Peretz", "Manager", "manager", "Appointed by Bob · Respond to inquiries");
        Node bob   = new Node("B", "Bob Mizrahi", "Owner",   "owner",   "Appointed by Alice · 2025-01-08", List.of(carol, dave));
        Node frank = new Node("F", "Frank Tal",  "Manager", "manager", "Appointed by Eve · Manage events, edit policies");
        Node eve   = new Node("E", "Eve Bar",    "Owner",   "owner",   "Appointed by Alice · 2025-02-14", List.of(frank));
        Node alice = new Node("A", "Alice Cohen", "Founder", "founder", "Founded 2024-12-15 · Live Nation Israel", List.of(bob, eve));

        card.add(new OrgTreeRenderer(alice));
        return card;
    }

    private Component buildLegendCard() {
        LkCard card = new LkCard("Legend").pad(16);
        Div row = new Div();
        row.getStyle().set("display", "flex").set("gap", "20px").set("flex-wrap", "wrap");
        row.add(
            legendItem("founder", "Founder",  "Immutable — created the company."),
            legendItem("owner",   "Owner",    "Appointed by founder or another owner."),
            legendItem("manager", "Manager",  "Appointed by an owner with granular permissions.")
        );
        card.add(row);
        return card;
    }

    private Component legendItem(String variant, String roleLabel, String desc) {
        Div item = new Div();
        item.getStyle().set("display", "flex").set("gap", "10px").set("align-items", "center");

        Span dot = new Span("●");
        dot.addClassName("oc-avatar");
        dot.addClassName("oc-av-" + variant);
        dot.getStyle().set("width", "26px").set("height", "26px").set("font-size", "12px");

        Span text = new Span();
        text.getElement().setProperty("innerHTML", "<b>" + roleLabel + "</b> · " + desc);
        text.getStyle().set("font-size", "13px");

        item.add(dot, text);
        return item;
    }
}
