package com.ticketing.system.Presentation.views.company;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ticketing.system.Core.Application.dto.CompanyPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.EventPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
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

    // ---------------------------------------------------------------------
    // Rule types — one per Domain policy class
    // ---------------------------------------------------------------------

    private enum RuleType {
        AgeAtLeast      ("years",   true),
        QuantityAtLeast ("tickets", true),
        QuantityAtMost  ("tickets", true);

        final String unit;
        final boolean hasValue;

        RuleType(String unit, boolean hasValue) { this.unit = unit; this.hasValue = hasValue; }

        String prettyEnglish(String value) {
            return switch (this) {
                case AgeAtLeast      -> "Age at least " + value + " years";
                case QuantityAtLeast -> "Quantity at least " + value + " tickets";
                case QuantityAtMost  -> "Quantity at most "  + value + " tickets";
            };
        }
    }

    // ---------------------------------------------------------------------
    // Policy model
    // ---------------------------------------------------------------------

    private enum Op { AND, OR }

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
    // Services & state
    // ---------------------------------------------------------------------

    private final EventManagementService   eventManagementService;
    private final CompanyManagementService companyManagementService;

    private final Composite root = seedSamplePolicy();
    private final Map<String, Node>      nodeMap   = new HashMap<>();
    private final Map<String, Composite> parentMap = new HashMap<>();

    private Span          plainEnglishText;
    private PolicyTreeMap mapPanel;
    private int companyId = -1;
    private int eventId   = -1;
    private boolean isEventLevel = true;

    // ---------------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------------

    public PurchasePolicyEditorView(EventManagementService   eventManagementService,
                                    CompanyManagementService companyManagementService) {
        this.eventManagementService   = eventManagementService;
        this.companyManagementService = companyManagementService;
    }

    // ---------------------------------------------------------------------
    // BeforeEnterObserver
    // ---------------------------------------------------------------------

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        RouteParameters params = event.getRouteParameters();
        params.get("eventId").ifPresent(id -> {
            try { this.eventId = Integer.parseInt(id); }
            catch (NumberFormatException ignored) { }
        });
        params.get("companyId").ifPresent(id -> {
    try { this.companyId = Integer.parseInt(id); }
    catch (NumberFormatException ignored) { }
});

        title("Purchase policies");
        subtitle("Click a node to edit it · drag the canvas to pan · scroll to zoom.");

        add(buildScopeToolbar());
        add(buildPlainEnglishBanner());
        add(buildCanvas());
        add(buildActionBar());
        add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 18),
            "Validation rejects empty groups and circular nesting before a policy can be saved."));
loadExistingPolicy();
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
// Load existing policy from backend
// ---------------------------------------------------------------------

private void loadExistingPolicy() {
    try {
        String token = resolveToken();
        if (token == null || companyId <= 0) return;
        PurchasePolicyDTO dto = isEventLevel && eventId > 0
            ? eventManagementService.getEventPurchasePolicy(token, companyId, eventId)
            : companyManagementService.getCompanyPurchasePolicy(token, companyId);
        if (dto != null && !"NONE".equals(dto.type())) {
            root.children.clear();
            root.op = Op.valueOf(dto.type());
            for (PurchasePolicyDTO child : dto.children()) {
                root.children.add(dtoToNode(child));
            }
        }
    } catch (Exception ignored) { }
}

private Node dtoToNode(PurchasePolicyDTO dto) {
    return switch (dto.type()) {
        case "AGE"         -> new Rule(RuleType.AgeAtLeast,      String.valueOf(dto.minimumAge()));
        case "MIN_TICKETS" -> new Rule(RuleType.QuantityAtLeast, String.valueOf(dto.minimumTickets()));
        case "MAX_TICKETS" -> new Rule(RuleType.QuantityAtMost,  String.valueOf(dto.maximumTickets()));
        case "AND", "OR"   -> {
            Composite c = new Composite(Op.valueOf(dto.type()));
            for (PurchasePolicyDTO child : dto.children()) c.children.add(dtoToNode(child));
            yield c;
        }
        default -> new Rule(RuleType.AgeAtLeast, "0");
    };
}



    // ---------------------------------------------------------------------
    // Scope toolbar
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

        companyBtn.addClickListener(e -> {
            isEventLevel = false;
            companyBtn.addClassName("on");
            eventBtn.removeClassName("on");
        });
        eventBtn.addClickListener(e -> {
            isEventLevel = true;
            eventBtn.addClassName("on");
            companyBtn.removeClassName("on");
        });

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
        String desc = c.op == Op.AND ? "all of these" : "any of these";
        return "{\"id\":\"" + c.id() + "\",\"kind\":\"comp\",\"op\":\""
             + c.op + "\",\"label\":\"" + escapeJson(c.op + " · " + desc)
             + "\",\"children\":[" + children + "]}";
    }

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
                    c.children.add(new Composite(Op.AND));
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
            r.value = valueField.getValue().trim();
            d.close();
            rebuildTree();
            Toasts.success("Rule updated.");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(cancel, save);
        d.open();
    }

    private void applyValueFieldState(TextField field, RuleType t) {
        field.setHelperText("Numeric value · unit = " + t.unit);
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
        opGroup.setItems(Op.AND, Op.OR);
        opGroup.setItemLabelGenerator(op -> switch (op) {
            case AND -> "AND · all children must be true";
            case OR  -> "OR · any child can be true";
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
            c.children.add(new Composite(Op.AND));
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
                .onClick(e -> savePolicy())
        );

        bar.add(left, right);
        return bar;
    }

    // ---------------------------------------------------------------------
    // Save
    // ---------------------------------------------------------------------

    private void savePolicy() {
        try {
            String token = resolveToken();
            if (token == null) {
                Toasts.failure("Not authenticated.");
                return;
            }

           if (companyId <= 0) {
           Toasts.failure("No company selected.");
               return;
}
            PurchasePolicyDTO dto = nodeToDTO(root);

            if (isEventLevel) {
                if (eventId <= 0) {
                    Toasts.failure("No event selected.");
                    return;
                }
                eventManagementService.setEventPolicies(token,
                        new EventPolicyConfigDTO(companyId, eventId, dto));
            } else {
                companyManagementService.setCompanyPolicies(token,
                        new CompanyPolicyConfigDTO(companyId, dto, List.of()));
            }

            Toasts.success("Policy saved.");

        } catch (Exception ex) {
            Toasts.failure("Failed to save: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Node → PurchasePolicyDTO — maps exactly to Domain classes
    // ---------------------------------------------------------------------

    private PurchasePolicyDTO nodeToDTO(Node node) {
        if (node instanceof Rule r) {
            int val = Integer.parseInt(r.value);
            return switch (r.type) {
                case AgeAtLeast      -> new PurchasePolicyDTO("AGE",         val,  null, null, null);
                case QuantityAtLeast -> new PurchasePolicyDTO("MIN_TICKETS", null, val,  null, null);
                case QuantityAtMost  -> new PurchasePolicyDTO("MAX_TICKETS", null, null, val,  null);
            };
        }
        Composite c = (Composite) node;
        List<PurchasePolicyDTO> children = c.children.stream()
                .map(this::nodeToDTO)
                .toList();
        String type = c.op == Op.AND ? "AND" : "OR";
        return new PurchasePolicyDTO(type, null, null, null, children);
    }

    // ---------------------------------------------------------------------
    // Auth token
    // ---------------------------------------------------------------------

    private String resolveToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object credentials = auth.getCredentials();
        if (credentials instanceof String s && !s.isBlank()) return s;
        Object principal = auth.getPrincipal();
        if (principal instanceof String s && !s.isBlank() && !s.equals("anonymousUser")) return s;
        return null;
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