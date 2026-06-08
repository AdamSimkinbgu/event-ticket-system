package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.venue.PolicyGroup;
import com.ticketing.system.Presentation.components.venue.PolicyRule;
import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.security.RequiresOwnerCompany;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/policies", layout = AdminLayout.class)
@PageTitle("Purchase policies · TicketHub")
@PermitAll
public class PurchasePolicyEditorView extends LkPage implements RequiresOwnerCompany {

    public PurchasePolicyEditorView() {
        title("Purchase policies");
        subtitle("Visual rule builder — author who is allowed to buy, in plain language.");

        add(buildScopeToolbar());
        add(buildPlainEnglishBanner());
        add(buildCanvas());
        add(buildActionBar());
        add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 18),
            "Validation rejects empty groups and circular nesting before a policy can be saved."));
    }

    private Component buildScopeToolbar() {
        Div bar = new Div();
        bar.addClassName("pe-toolbar");

        Div scope = new Div();
        scope.addClassName("pe-scope");
        Span lbl = new Span("Scope");
        lbl.addClassName("pe-scope-label");
        scope.add(lbl);

        // Segmented Company-level / Event-level
        Div seg = new Div();
        seg.addClassName("pe-seg");
        NativeButton companyBtn = new NativeButton("Company-level");
        companyBtn.addClassName("pe-seg-opt");
        NativeButton eventBtn = new NativeButton("Event-level");
        eventBtn.addClassName("pe-seg-opt");
        eventBtn.addClassName("on");
        seg.add(companyBtn, eventBtn);
        scope.add(seg);

        Span ctx = new Span();
        ctx.addClassName("pe-scope-ctx");
        ctx.add(new LkIcon("ticket", 16));
        Span ctxLabel = new Span();
        ctxLabel.getElement().setProperty("innerHTML", "<b>Coldplay · Music of the Spheres</b>");
        ctx.add(ctxLabel);
        ctx.add(new LkIcon("caret", 14));
        scope.add(ctx);

        Div status = new Div();
        status.addClassName("pe-status");
        Span valid = new Span();
        valid.addClassName("pe-valid");
        valid.addClassName("ok");
        Span dot = new Span("●");
        dot.addClassName("dot");
        valid.add(dot, new Span(" Valid"));
        Span saved = new Span("Saved 2 min ago");
        saved.addClassName("pe-saved");
        status.add(valid, saved);

        bar.add(scope, status);
        return bar;
    }

    private Component buildPlainEnglishBanner() {
        Div plain = new Div();
        plain.addClassName("pe-plain");

        Span emoji = new Span();
        emoji.addClassName("pe-plain-emoji");
        emoji.add(new LkIcon("comment", 22));

        Div text = new Div();
        Span kicker = new Span("In plain English");
        kicker.addClassName("pe-plain-k");
        Span body = new Span();
        body.addClassName("pe-plain-text");
        body.getElement().setProperty("innerHTML",
            "Buyer must be <b>at least 18</b> <span class='or'>and</span> buy <b>between 1 and 4 tickets</b>.");
        text.add(kicker, body);

        plain.add(emoji, text);
        return plain;
    }

    private Component buildCanvas() {
        Div canvas = new Div();
        canvas.addClassName("pe-canvas");

        Div inner = new Div();
        inner.addClassName("pe-canvas-inner");

        PolicyGroup root = new PolicyGroup("AND", true);
        root.add(new PolicyRule("AgeAtLeast", "≥", "18", "years"));

        PolicyGroup or = new PolicyGroup("OR", false);
        or.add(new PolicyRule("QuantityAtLeast", "≥", "1", "tickets"));
        or.add(new PolicyRule("QuantityAtMost",  "≤", "4", "tickets"));
        root.add(or);

        inner.add(root);
        canvas.add(inner);
        return canvas;
    }

    private Component buildActionBar() {
        Div bar = new Div();
        bar.addClassName("pe-actionbar");

        Div left = new Div(); left.addClassName("l");
        left.add(new LkBtn("Discard changes").variant(LkBtn.Variant.tertiary)
            .onClick(e -> {
                Toasts.warn("Changes discarded.");
                UI.getCurrent().navigate(CompanyEventListView.class);
            }));

        Div right = new Div(); right.addClassName("r");
        right.add(
            new LkBtn("Test against a buyer").variant(LkBtn.Variant.secondary)
                .icon(new LkIcon("flask", 16))
                .onClick(e -> Toasts.warn("Test-against-a-buyer dialog (V2-PEDIT-02).")),
            new LkBtn("Save policy").variant(LkBtn.Variant.primary)
                .onClick(e -> Toasts.success("Policy saved."))
        );

        bar.add(left, right);
        return bar;
    }
}
