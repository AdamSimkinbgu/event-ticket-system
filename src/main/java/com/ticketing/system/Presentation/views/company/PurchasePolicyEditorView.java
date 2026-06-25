package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.Group;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.LoadOutcome;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.Node;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.Op;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.Rule;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.RuleType;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.SaveOutcome;
import com.ticketing.system.Presentation.presenters.company.PurchasePolicyEditorPresenter.Validity;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.policy.PolicyTreeMap;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import jakarta.annotation.security.PermitAll;

@Route(value = "owner/policies/:companyId/:eventId", layout = WorkspaceLayout.class)
@PageTitle("Purchase policies · TicketHub")
@PermitAll
@RequireCapability(Capability.EDIT_PURCHASE_POLICIES)
public class PurchasePolicyEditorView extends LkPage implements BeforeEnterObserver {

    private final PurchasePolicyEditorPresenter presenter;

    private Group   root;
    private int     companyId    = -1;
    private int     eventId      = -1;
    private boolean isEventLevel = true;
    private String  memberToken;

    private Span          plainEnglishText;
    private PolicyTreeMap mapPanel;
    private Span          validityBadge;
    private Span          scopeContextLabel;
    private final Div     loadErrorSlot = new Div();

    public PurchasePolicyEditorView(PurchasePolicyEditorPresenter presenter) {
        this.presenter = presenter;
        this.root = presenter.emptyPolicy();
    }


    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        RouteParameters params = event.getRouteParameters();
        params.get("companyId").ifPresent(id -> {
            try { this.companyId = Integer.parseInt(id); } catch (NumberFormatException ignored) { }
        });
        params.get("eventId").ifPresent(id -> {
            try { this.eventId = Integer.parseInt(id); } catch (NumberFormatException ignored) { }
        });

        resolveIdentity();

        title("Purchase policies");
        subtitle("Click a node to edit it · drag the canvas to pan · scroll to zoom.");

        add(buildScopeToolbar());
        add(loadErrorSlot);
        add(buildPlainEnglishBanner());
        add(buildCanvas());
        add(buildActionBar());
        add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 18),
            "Validation rejects empty groups and circular nesting before a policy can be saved."));

        loadExistingPolicy();
        rebuildTree();
    }

    private void resolveIdentity() {
        this.memberToken = presenter.resolveIdentity().memberToken();
    }

    private void loadExistingPolicy() {
        loadErrorSlot.removeAll();
        if (companyId <= 0) return;
        switch (presenter.load(memberToken, companyId, eventId, isEventLevel)) {
            case LoadOutcome.Success s -> this.root = s.root();
            case LoadOutcome.NotAuthenticated na -> Toasts.warn("Please sign in again to edit policies.");
            case LoadOutcome.Failure f -> {
                LkBanner err = new LkBanner(LkBanner.Tone.error,
                    new LkIcon("alert-circle", 16),
                    "Could not load existing policy: " + f.reason());
                LkBtn retry = new LkBtn("Retry").variant(LkBtn.Variant.secondary);
                retry.addClickListener(ev -> { loadExistingPolicy(); rebuildTree(); });
                err.setAction(retry);
                loadErrorSlot.add(err);
            }
        }
    }


    private Component buildScopeToolbar() {
        Div bar = new Div(); bar.addClassName("pe-toolbar");

        Div scope = new Div(); scope.addClassName("pe-scope");
        Span lbl = new Span("Scope"); lbl.addClassName("pe-scope-label");
        scope.add(lbl);

        Div seg = new Div(); seg.addClassName("pe-seg");
        NativeButton companyBtn = new NativeButton("Company-level"); companyBtn.addClassName("pe-seg-opt");
        NativeButton eventBtn   = new NativeButton("Event-level");   eventBtn.addClassName("pe-seg-opt");
        if (isEventLevel) eventBtn.addClassName("on"); else companyBtn.addClassName("on");

        companyBtn.addClickListener(e -> {
            isEventLevel = false;
            companyBtn.addClassName("on"); eventBtn.removeClassName("on");
            loadExistingPolicy(); rebuildTree();
        });
        eventBtn.addClickListener(e -> {
            isEventLevel = true;
            eventBtn.addClassName("on"); companyBtn.removeClassName("on");
            loadExistingPolicy(); rebuildTree();
        });
        seg.add(companyBtn, eventBtn);
        scope.add(seg);

        Div ctx = new Div(); ctx.addClassName("pe-scope-ctx");
        ctx.add(new LkIcon("ticket", 16));
        scopeContextLabel = new Span();
        ctx.add(scopeContextLabel);
        scope.add(ctx);

        Div status = new Div(); status.addClassName("pe-status");
        validityBadge = new Span(); validityBadge.addClassName("pe-valid");
        status.add(validityBadge);

        bar.add(scope, status);
        return bar;
    }

    private void refreshScope() {
        if (scopeContextLabel == null) return;
        scopeContextLabel.setText(isEventLevel
            ? (eventId   > 0 ? "Event #"   + eventId   : "Event-level")
            : (companyId > 0 ? "Company #" + companyId : "Company-level"));
    }

    private void updateValidity() {
        if (validityBadge == null) return;
        Validity v = presenter.validate(root);
        validityBadge.removeAll();
        validityBadge.add(new Span("● "), new Span(v.valid() ? "Valid" : v.message()));
        validityBadge.getStyle()
            .set("color", v.valid() ? "#16a34a" : "#dc2626")
            .set("font-size", "12.5px")
            .set("font-weight", "600");
    }

    // ---------------------------------------------------------------------
    // Plain-English banner
    // ---------------------------------------------------------------------

    private Component buildPlainEnglishBanner() {
        Div plain = new Div(); plain.addClassName("pe-plain");
        Span emoji = new Span(); emoji.addClassName("pe-plain-emoji"); emoji.add(new LkIcon("comment", 22));
        Div text = new Div();
        Span kicker = new Span("In plain English"); kicker.addClassName("pe-plain-k");
        plainEnglishText = new Span(); plainEnglishText.addClassName("pe-plain-text");
        text.add(kicker, plainEnglishText);
        plain.add(emoji, text);
        return plain;
    }

    // ---------------------------------------------------------------------
    // Canvas
    // ---------------------------------------------------------------------

    private Component buildCanvas() {
        mapPanel = new PolicyTreeMap();
        mapPanel.setOnAction(this::handleNodeAction);
        return mapPanel;
    }

    /** Re-render every view surface from the current model (all data comes from the presenter). */
    private void rebuildTree() {
        mapPanel.render(presenter.toTreeJson(root));
        plainEnglishText.getElement().setProperty("innerHTML", presenter.toPlainEnglishHtml(root));
        refreshScope();
        updateValidity();
    }

    private void handleNodeAction(String nodeId, String action) {
        Node n = presenter.findNode(root, nodeId);
        if (n == null) return;
        switch (action) {
            case "click" -> {
                if (n instanceof Rule r) openRuleDialog(r);
                else if (n instanceof Group g) openGroupDialog(g);
            }
            case "addRule" -> {
                presenter.addRule(root, nodeId);
                rebuildTree();
                Toasts.success("Rule added — click it to edit.");
            }
            case "addGroup" -> {
                presenter.addGroup(root, nodeId);
                rebuildTree();
                Toasts.success("Group added — click it to add children.");
            }
        }
    }

    // ---------------------------------------------------------------------
    // Rule editor dialog
    // ---------------------------------------------------------------------

    private void openRuleDialog(Rule r) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Edit rule");
        d.setWidth("420px");

        Select<RuleType> typeSelect = new Select<>();
        typeSelect.setLabel("Rule type");
        typeSelect.setItems(RuleType.values());
        typeSelect.setItemLabelGenerator(Enum::name);
        typeSelect.setValue(r.type());
        typeSelect.setWidthFull();

        TextField valueField = new TextField("Value");
        valueField.setValue(r.value() == null ? "" : r.value());
        valueField.setWidthFull();
        applyValueHelper(valueField, typeSelect.getValue());
        typeSelect.addValueChangeListener(e -> applyValueHelper(valueField, e.getValue()));

        Div body = new Div(typeSelect, valueField);
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px");
        d.add(body);

        if (presenter.canDelete(root, r.id())) {
            Button delete = new Button("Delete rule", e -> {
                presenter.deleteNode(root, r.id());
                d.close(); rebuildTree(); Toasts.warn("Rule removed.");
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.getStyle().set("margin-right", "auto");
            d.getFooter().add(delete);
        }

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Save", e -> {
            presenter.updateRule(root, r.id(), typeSelect.getValue(), valueField.getValue());
            d.close(); rebuildTree(); Toasts.success("Rule updated.");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(cancel, save);
        d.open();
    }

    private void applyValueHelper(TextField field, RuleType t) {
        field.setHelperText("Numeric value · unit = " + t.unit());
    }

    // ---------------------------------------------------------------------
    // Group editor dialog
    // ---------------------------------------------------------------------

    private void openGroupDialog(Group g) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Edit group");
        d.setWidth("440px");

        RadioButtonGroup<Op> opGroup = new RadioButtonGroup<>();
        opGroup.setLabel("Operator");
        opGroup.setItems(Op.AND, Op.OR);
        opGroup.setItemLabelGenerator(op -> switch (op) {
            case AND -> "AND · all children must be true";
            case OR  -> "OR · any child can be true";
        });
        opGroup.setValue(g.op());

        Button addRule = new Button("+ Add rule", e -> {
            presenter.addRule(root, g.id());
            d.close(); rebuildTree(); Toasts.success("Rule added — click it to edit.");
        });
        addRule.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);

        Button addGroup = new Button("+ Add group", e -> {
            presenter.addGroup(root, g.id());
            d.close(); rebuildTree(); Toasts.success("Group added — click it to add children.");
        });
        addGroup.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div addRow = new Div(addRule, addGroup);
        addRow.getStyle().set("display", "flex").set("gap", "8px").set("margin-top", "4px");

        Div body = new Div(opGroup, addRow);
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px");
        d.add(body);

        if (presenter.canDelete(root, g.id())) {
            Button delete = new Button("Delete group", e -> {
                presenter.deleteNode(root, g.id());
                d.close(); rebuildTree(); Toasts.warn("Group removed.");
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.getStyle().set("margin-right", "auto");
            d.getFooter().add(delete);
        }

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Save", e -> {
            presenter.updateGroupOp(root, g.id(), opGroup.getValue());
            d.close(); rebuildTree(); Toasts.success("Group updated.");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(cancel, save);
        d.open();
    }

    // ---------------------------------------------------------------------
    // Action bar + save
    // ---------------------------------------------------------------------

    private Component buildActionBar() {
        Div bar = new Div(); bar.addClassName("pe-actionbar");

        Div left = new Div(); left.addClassName("l");
        left.add(new LkBtn("Discard changes").variant(LkBtn.Variant.tertiary)
            .onClick(e -> { loadExistingPolicy(); rebuildTree(); Toasts.warn("Changes discarded."); }));

        Div right = new Div(); right.addClassName("r");
        right.add(new LkBtn("Save policy").variant(LkBtn.Variant.primary)
            .onClick(e -> savePolicy()));

        bar.add(left, right);
        return bar;
    }

    private void savePolicy() {
        if (companyId <= 0) { Toasts.failure("No company selected."); return; }
        if (isEventLevel && eventId <= 0) { Toasts.failure("No event selected."); return; }
        switch (presenter.save(memberToken, companyId, eventId, isEventLevel, root)) {
            case SaveOutcome.Success s        -> Toasts.success("Policy saved.");
            case SaveOutcome.NotAuthenticated na -> Toasts.failure("Please sign in again to save.");
            case SaveOutcome.Invalid inv      -> Toasts.warn(inv.reason());
            case SaveOutcome.Failure f        -> Toasts.failure("Failed to save: " + f.reason());
        }
    }
}