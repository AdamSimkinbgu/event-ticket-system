package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Core.Application.dto.OrganizationalTreeNodeDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Presentation.components.admin.OrgTreeRenderer;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkFilterChip;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.PlatformAdminLayout;
import com.ticketing.system.Presentation.presenters.admin.OrgTreePresenter;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.AuthSession;
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

    private final OrgTreePresenter presenter;
    private final Div body = new Div();
    private Integer selectedCompanyId = null;

    public OrganizationalTreeView(OrgTreePresenter presenter) {
        this.presenter = presenter;
        title("Organizational Tree");
        subtitle("Founder → owners → managers hierarchy for the selected company, with audit lines.");
        add(body);
        render();
    }

    private void render() {
        body.removeAll();

        switch (presenter.load(AuthSession.token(), selectedCompanyId, AuthSession.isAdmin())) {
            case OrgTreePresenter.Outcome.NotAuthenticated ignored ->
                body.add(new LkBanner(LkBanner.Tone.warn, new LkIcon("lock", 16),
                    "Your session has expired — please sign in again."));
            case OrgTreePresenter.Outcome.NoCompany ignored ->
                body.add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 16),
                    AuthSession.isAdmin()
                        ? "No companies exist in the system yet."
                        : "You have no owned companies."));
            case OrgTreePresenter.Outcome.Failure fail ->
                body.add(new LkBanner(LkBanner.Tone.error, new LkIcon("warning", 16),
                    "Could not load the tree: " + fail.reason()));
            case OrgTreePresenter.Outcome.Success ok -> {
                body.add(buildFilters(ok.companies(), ok.selected()));
                body.add(buildTreeCard(ok.selected().name(), ok.tree()));
                body.add(buildLegendCard());
            }
        }
    }

    private Component buildFilters(List<ProductionCompanyDTO> companies, ProductionCompanyDTO selected) {
        LkRow row = new LkRow().gap(8);

        List<String> names = companies.stream().map(ProductionCompanyDTO::name).toList();
        LkFilterChip companyChip = new LkFilterChip("Company", names, true, List.of(selected.name()));
        companyChip.onApply(() -> {
            String picked = companyChip.getSelected().stream().findFirst().orElse(selected.name());
            selectedCompanyId = companies.stream()
                    .filter(c -> c.name().equals(picked))
                    .map(ProductionCompanyDTO::companyId)
                    .findFirst()
                    .orElse(selected.companyId());
            render();
        });

        row.add(
            companyChip,
            new LkFilterChip("Roles", List.of("Founder", "Owners", "Managers"), true,
                List.of("Founder", "Owners", "Managers"))
        );
        return row;
    }

    private Component buildTreeCard(String companyName, OrganizationalTreeNodeDTO root) {
        LkCard card = new LkCard(companyName + " — Appointment Hierarchy").pad(20);
        card.add(new OrgTreeRenderer(root));
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
