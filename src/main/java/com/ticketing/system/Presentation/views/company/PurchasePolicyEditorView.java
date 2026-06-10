package com.ticketing.system.Presentation.views.company;

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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Route(value = "owner/policies", layout = WorkspaceLayout.class)
@PageTitle("Purchase policies · TicketHub")
@PermitAll
@RequireCapability(Capability.EDIT_PURCHASE_POLICIES)
public class PurchasePolicyEditorView extends LkPage {

    // ---------------------------------------------------------------------
    // Built-in rule registry — single source of truth for rule types.
    // ---------------------------------------------------------------------

    private enum RuleType {
        AgeAtLeast        ("years",      true),
        AgeAtMost         ("years",      true),
        QuantityAtLeast   ("tickets",    true),
        QuantityAtMost    ("tickets",    true),
        MembershipRequired("",           false),
        PurchaseLimit     ("per buyer",  true),
        DateWindow        ("",           false);

        final String unit;
        final boolean hasValue;

        RuleType(String unit, boolean hasValue) { this.unit = unit; this.hasValue = hasValue; }

        String prettyEnglish(String value) {
            return switch (this) {
                case AgeAtLeast        -> "Age at least " + value + " years";
                case AgeAtMost         -> "Age at most "  + value + " years";
                case QuantityAtLeast   -> "Quantity at least " + value + " tickets";
                case QuantityAtMost    -> "Quantity at most "  + value + " tickets";
                case MembershipRequired -> "Membership required";
                case PurchaseLimit     -> "Purchase limit " + value + " per buyer";
                case DateWindow        -> "Within configured date window";
            };
        }
    }

    // ---------------------------------------------------------------------
    // Mock policy model.
    // ---------------------------------------------------------------------

    private enum Op { AND, OR, NOT }

    private sealed interface Node permits Composite, Rule {
        String id();
    }

    private static final class Composite implements Node {
        final String id = "n-" + UUID.randomUUID().toString().substring(0, 8);
        Op op;
        final List<Node> children = new ArrayList<>();
        Composite(Op op) { this.op = op; }
        public String id() { return id; }
    }

    private static final class Rule implements Node {
        final String id = "n-" + UUID.randomUUID().toString().substring(0, 8);
        RuleType type;
        String value;
        Rule(RuleType type, String value) { this.type = type; this.value = value; }
        public String id() { return id; }
    }

    // ---------------------------------------------------------------------
    // View state
    // ---------------------------------------------------------------------

    private final Composite root = seedSamplePolicy();
    private final Map<String, Node> nodeMap = new HashMap<>();
    private final Map<String, Composite> parentMap = new HashMap<>();

    private Span plainEnglishText;
    private PolicyTreeMap mapPanel;

    public PurchasePolicyEditorView() {
        title("Purchase policies");
        subtitle("Click a node to edit it · drag the canvas to pan · scroll to zoom.");

        add(buildScopeToolbar());
        add(buildPlainEnglishBanner());
        add(buildCanvas());
        add(buildActionBar());
        add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 18),
            "Validation rejects empty groups and circular nesting before a policy can be saved."));

        rebuildTree();
    }

    private static Composite seedSamplePolicy() {
        Composite andRoot = new Composite(Op.AND);
        andRoot.children.add(new Rule(RuleType.AgeAtLeast, "18"));
        Composite or = new Composite(Op.OR);
        or.children.add(new Rule(RuleType.QuantityAtLeast, "1"));
        or.children.add(new Rule(RuleType.QuantityAtMost,  "4"));
        andRoot.children.add(or);
        return andRoot;
    }

    // ---------------------------------------------------------------------
    // Scope toolbar (unchanged)
    // ---------------------------------------------------------------------

    private Component buildScopeToolbar() {
        Div bar = new Div();
        bar.addClassName("pe-toolbar");

        Div scope = new Div();
        scope.addClassName("pe-scope");
        Span lbl = new Span("Scope");
        lbl.addClassName("pe-scope-label");
        scope.add(lbl);

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

    // ---------------------------------------------------------------------
    // Plain-English banner
    // ---------------------------------------------------------------------

    private Component buildPlainEnglishBanner() {
        Div plain = new Div();
        plain.addClassName("pe-plain");

        Span emoji = new Span();
        emoji.addClassName("pe-plain-emoji");
        emoji.add(new LkIcon("comment", 22));

        Div text = new Div();
        Span kicker = new Span("In plain English");
        kicker.addClassName("pe-plain-k");
        plainEnglishText = new Span();
        plainEnglishText.addClassName("pe-plain-text");
        text.add(kicker, plainEnglishText);

        plain.add(emoji, text);
        return plain;
    }

    private String renderEnglish(Node node) {
        if (node instanceof Rule r) {
            return "<b>" + escape(r.type.prettyEnglish(r.value)) + "</b>";
        }
        Composite c = (Composite) node;
        if (c.children.isEmpty()) {
            return "<i>(empty " + c.op + " group)</i>";
        }
        if (c.op == Op.NOT) {
            return "NOT (" + joinChildren(c.children, "OR") + ")";
        }
        String joiner = c.op == Op.AND ? "AND" : "OR";
        return joinChildren(c.children, joiner);
    }

    private String joinChildren(List<Node> children, String joiner) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) sb.append(" <span class='or'>").append(joiner).append("</span> ");
            Node ch = children.get(i);
            if (ch instanceof Composite) sb.append("(").append(renderEnglish(ch)).append(")");
            else                          sb.append(renderEnglish(ch));
        }
        return sb.toString();
    }

    private void refreshPlainEnglish() {
        plainEnglishText.getElement().setProperty("innerHTML",
            "Buyer must satisfy " + renderEnglish(root) + ".");
    }

    // ---------------------------------------------------------------------
    // Canvas — D3 tree map
    // ---------------------------------------------------------------------

    private Component buildCanvas() {
        mapPanel = new PolicyTreeMap();
        mapPanel.setOnAction(this::handleNodeAction);
        return mapPanel;
    }

    private void rebuildTree() {
        nodeMap.clear();
        parentMap.clear();
        walk(root, null);
        mapPanel.render(treeToJson(root));
        refreshPlainEnglish();
    }

    private void walk(Node node, Composite parent) {
        nodeMap.put(node.id(), node);
        if (parent != null) parentMap.put(node.id(), parent);
        if (node instanceof Composite c) {
            for (Node child : c.children) walk(child, c);
        }
    }

    private String treeToJson(Node n) {
        if (n instanceof Rule r) {
            return "{\"id\":\"" + r.id() + "\",\"kind\":\"rule\",\"label\":\""
                 + escapeJson(r.type.prettyEnglish(r.value == null ? "" : r.value)) + "\"}";
        }
        Composite c = (Composite) n;
        StringBuilder children = new StringBuilder();
        for (int i = 0; i < c.children.size(); i++) {
            if (i > 0) children.append(",");
            children.append(treeToJson(c.children.get(i)));
        }
        String desc = switch (c.op) {
            case AND -> "all of these";
            case OR  -> "any of these";
            case NOT -> "none of these";
        };
        String label = c.op + " · " + desc;
        return "{\"id\":\"" + c.id() + "\",\"kind\":\"comp\",\"op\":\""
             + c.op + "\",\"label\":\"" + escapeJson(label)
             + "\",\"children\":[" + children + "]}";
    }

    /**
     * Three actions come back from the D3 layer:
     * {@code "click"} → open the appropriate edit dialog;
     * {@code "addRule"} / {@code "addGroup"} → fired by the inline +R / +G
     * pills next to each composite. The pills mutate the tree directly,
     * no dialog needed for the common case.
     */
    private void handleNodeAction(String nodeId, String action) {
        Node n = nodeMap.get(nodeId);
        if (n == null) return;
        switch (action) {
            case "click" -> {
                if (n instanceof Rule r) openRuleDialog(r);
                else if (n instanceof Composite c) openCompositeDialog(c);
            }
            case "addRule" -> {
                if (n instanceof Composite c) {
                    c.children.add(new Rule(RuleType.AgeAtLeast, "18"));
                    rebuildTree();
                    Toasts.success("Rule added — click it to edit.");
                }
            }
            case "addGroup" -> {
                if (n instanceof Composite c) {
                    c.children.add(new Composite(nextOp(c.op)));
                    rebuildTree();
                    Toasts.success("Group added — click it to add children.");
                }
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
        typeSelect.setValue(r.type);
        typeSelect.setWidthFull();

        TextField valueField = new TextField("Value");
        valueField.setValue(r.value == null ? "" : r.value);
        valueField.setWidthFull();
        applyValueFieldState(valueField, typeSelect.getValue());
        typeSelect.addValueChangeListener(e -> applyValueFieldState(valueField, e.getValue()));

        Div body = new Div(typeSelect, valueField);
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px");
        d.add(body);

        Composite parent = parentMap.get(r.id());
        if (parent != null) {
            Button delete = new Button("Delete rule", e -> {
                parent.children.removeIf(child -> r.id().equals(child.id()));
                d.close();
                rebuildTree();
                Toasts.warn("Rule removed.");
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.getStyle().set("margin-right", "auto");
            d.getFooter().add(delete);
        }

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Save", e -> {
            RuleType chosen = typeSelect.getValue();
            r.type = chosen;
            if (chosen.hasValue && !valueField.getValue().isBlank()) {
                r.value = valueField.getValue().trim();
            } else if (!chosen.hasValue) {
                r.value = "";
            }
            d.close();
            rebuildTree();
            Toasts.success("Rule updated.");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(cancel, save);

        d.open();
    }

    private void applyValueFieldState(TextField field, RuleType t) {
        field.setEnabled(t.hasValue);
        field.setHelperText(t.hasValue
            ? "Numeric value · unit = " + t.unit
            : "This rule has no value to configure.");
    }

    // ---------------------------------------------------------------------
    // Composite (group) editor dialog
    // ---------------------------------------------------------------------

    private void openCompositeDialog(Composite c) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Edit group");
        d.setWidth("440px");

        RadioButtonGroup<Op> opGroup = new RadioButtonGroup<>();
        opGroup.setLabel("Operator");
        opGroup.setItems(Op.AND, Op.OR, Op.NOT);
        opGroup.setItemLabelGenerator(op -> switch (op) {
            case AND -> "AND · all children must be true";
            case OR  -> "OR · any child can be true";
            case NOT -> "NOT · none of the children can be true";
        });
        opGroup.setValue(c.op);

        Button addRule = new Button("+ Add rule", e -> {
            c.children.add(new Rule(RuleType.AgeAtLeast, "18"));
            d.close();
            rebuildTree();
            Toasts.success("Rule added — click it to edit.");
        });
        addRule.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);

        Button addGroup = new Button("+ Add group", e -> {
            c.children.add(new Composite(nextOp(c.op)));
            d.close();
            rebuildTree();
            Toasts.success("Group added — click it to add children.");
        });
        addGroup.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Div addRow = new Div(addRule, addGroup);
        addRow.getStyle().set("display", "flex").set("gap", "8px").set("margin-top", "4px");

        Div body = new Div(opGroup, addRow);
        body.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "12px");
        d.add(body);

        Composite parent = parentMap.get(c.id());
        if (parent != null) {
            Button delete = new Button("Delete group", e -> {
                parent.children.removeIf(child -> c.id().equals(child.id()));
                d.close();
                rebuildTree();
                Toasts.warn("Group removed.");
            });
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.getStyle().set("margin-right", "auto");
            d.getFooter().add(delete);
        }

        Button cancel = new Button("Cancel", e -> d.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button save = new Button("Save", e -> {
            c.op = opGroup.getValue();
            d.close();
            rebuildTree();
            Toasts.success("Group updated.");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(cancel, save);

        d.open();
    }

    private static Op nextOp(Op current) {
        return switch (current) {
            case AND -> Op.OR;
            case OR  -> Op.NOT;
            case NOT -> Op.AND;
        };
    }

    // ---------------------------------------------------------------------
    // Action bar
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
