package com.ticketing.system.Presentation.dev;

import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.MockCart;
import com.ticketing.system.Presentation.session.MockCompanies;
import com.ticketing.system.Presentation.session.MockPermissions;
import com.ticketing.system.Presentation.session.MockSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;

import java.util.List;
import java.util.UUID;

/**
 * Development-only session override widget. Renders as a floating
 * "BUG · DEV" pill in the bottom-right corner of every page; clicking
 * opens a dialog that lets us flip the current persona, edit the
 * user's identity, switch between companies, toggle manager grants,
 * and inspect the resulting capability set — all without touching
 * the actual sign-in forms.
 *
 * <p>Attached globally by {@link DevPanelInitializer} on every UI
 * init, so it shows up regardless of which layout is rendered.
 */
public final class DevPanel {

    private static final String SECTION_LABEL_COLOR = "#475569";
    private static final String SECTION_SUB_COLOR   = "#94a3b8";
    private static final String DIVIDER_COLOR       = "#e2e8f0";

    private DevPanel() { }

    /** Floating trigger pill in the bottom-right corner. */
    public static Button trigger() {
        Icon bug = new Icon(VaadinIcon.BUG);
        bug.getStyle().set("width", "14px").set("height", "14px");
        Button b = new Button("DEV", bug, e -> show());
        b.getElement().setAttribute("aria-label", "Open dev panel");
        b.getStyle()
            .set("position", "fixed")
            .set("bottom", "20px")
            .set("right", "20px")
            .set("z-index", "9999")
            .set("background", "#0f172a")
            .set("color", "#fff")
            .set("border", "1px solid #334155")
            .set("border-radius", "999px")
            .set("padding", "8px 16px")
            .set("font-family", "var(--mono)")
            .set("font-weight", "800")
            .set("font-size", "12px")
            .set("letter-spacing", ".06em")
            .set("box-shadow", "0 6px 20px rgba(15,23,42,0.4)")
            .set("cursor", "pointer");
        return b;
    }

    /** Show the override dialog. */
    public static void show() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Dev panel — session override");
        dialog.setWidth("680px");
        dialog.setMaxWidth("96vw");
        dialog.setDraggable(true);

        Div content = new Div();
        content.getStyle().set("display", "flex").set("flex-direction", "column")
            .set("gap", "0").set("max-height", "70vh").set("overflow", "auto")
            .set("padding", "4px 2px");

        content.add(
            section("Persona shortcuts",  "Click to toggle. Member auto-engages with role buttons; Admin stacks with the rest.",
                buildPersonaRow(dialog)),
            section("Identity",           null,
                buildIdentityRow()),
            section("Companies",          "Drives OWNER_WORKSPACE + the manager-vs-owner branch.",
                buildCompaniesBlock(dialog)),
            section("Manager permissions", currentManagerSubtitle(),
                buildManagerPermsBlock()),
            section("Capability inspector", "Live from Capabilities.forCurrentUser()",
                buildCapabilityCloud())
        );
        dialog.add(content);

        Button close = new Button("Close", e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button reload = new Button("Apply & reload", e -> {
            dialog.close();
            UI.getCurrent().getPage().reload();
        });
        reload.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(close, reload);

        dialog.open();
    }

    // ---------------------------------------------------------------------
    // Layout helpers
    // ---------------------------------------------------------------------

    /** Standard section: small uppercase label, optional muted subtitle, content. */
    private static Div section(String label, String subtitle, Component content) {
        Div sect = new Div();
        sect.getStyle()
            .set("display", "flex").set("flex-direction", "column")
            .set("gap", "8px").set("padding", "14px 0")
            .set("border-bottom", "1px solid " + DIVIDER_COLOR);

        Span lbl = new Span(label);
        lbl.getStyle()
            .set("font-size", "11px").set("font-weight", "800")
            .set("letter-spacing", ".08em").set("text-transform", "uppercase")
            .set("color", SECTION_LABEL_COLOR);
        sect.add(lbl);

        if (subtitle != null && !subtitle.isEmpty()) {
            Span sub = new Span(subtitle);
            sub.getStyle()
                .set("font-size", "12.5px")
                .set("color", SECTION_SUB_COLOR)
                .set("margin-top", "-4px").set("line-height", "1.4");
            sect.add(sub);
        }
        sect.add(content);
        return sect;
    }

    private static Div hrow(int gap, Component... children) {
        Div d = new Div();
        d.getStyle().set("display", "flex").set("flex-wrap", "wrap")
            .set("align-items", "center").set("gap", gap + "px");
        d.add(children);
        return d;
    }

    private static Div vcol(int gap, Component... children) {
        Div d = new Div();
        d.getStyle().set("display", "flex").set("flex-direction", "column")
            .set("gap", gap + "px");
        d.add(children);
        return d;
    }

    private static Button pillBtn(String label, String bg, String fg, Runnable onClick) {
        Button b = new Button(label, e -> onClick.run());
        b.getStyle()
            .set("background", bg).set("color", fg)
            .set("border", "1px solid " + DIVIDER_COLOR)
            .set("border-radius", "7px")
            .set("padding", "5px 12px")
            .set("font-size", "12.5px").set("font-weight", "700")
            .set("min-width", "auto");
        return b;
    }

    private static Button ghostBtn(String label, Runnable onClick) {
        return pillBtn(label, "#fff", "#1e293b", onClick);
    }

    private static Button dangerBtn(String label, Runnable onClick) {
        Button b = pillBtn(label, "#fff", "#b91c1c", onClick);
        b.getStyle().set("border-color", "#fca5a5");
        return b;
    }

    // ---------------------------------------------------------------------
    // Persona shortcuts — one row of buttons
    // ---------------------------------------------------------------------

    private static Component buildPersonaRow(Dialog dialog) {
        return hrow(6,
            personaToggle("Guest",    isGuestSelected(),    () -> togglePersona("Guest",    dialog)),
            personaToggle("Member",   isMemberSelected(),   () -> togglePersona("Member",   dialog)),
            personaToggle("Manager",  hasRole("Manager"),   () -> togglePersona("Manager",  dialog)),
            personaToggle("Co-owner", hasRole("Co-owner"),  () -> togglePersona("Co-owner", dialog)),
            personaToggle("Founder",  hasRole("Founder"),   () -> togglePersona("Founder",  dialog)),
            personaToggle("Admin",    AuthSession.isAdmin(),   () -> togglePersona("Admin",    dialog))
        );
    }

    /** Pill button that swaps fill / outline based on {@code selected}. */
    private static Button personaToggle(String label, boolean selected, Runnable onClick) {
        Button b = new Button(label, e -> onClick.run());
        b.getStyle()
            .set("border-radius", "999px")
            .set("padding", "5px 14px")
            .set("font-size", "12.5px")
            .set("font-weight", "700")
            .set("cursor", "pointer")
            .set("min-width", "auto");
        if (selected) {
            b.getStyle()
                .set("background", "#0f172a").set("color", "#fff")
                .set("border", "1px solid #0f172a");
        } else {
            b.getStyle()
                .set("background", "#fff").set("color", "#1e293b")
                .set("border", "1px solid " + DIVIDER_COLOR);
        }
        return b;
    }

    private static boolean isGuestSelected() {
        return !AuthSession.isSignedIn() && !AuthSession.isAdmin();
    }

    private static boolean isMemberSelected() {
        return AuthSession.isSignedIn();
    }

    private static boolean hasRole(String role) {
        return MockCompanies.forCurrentUser().stream()
            .anyMatch(c -> role.equals(c.role()));
    }

    private static boolean hasAnyCompanyRole() {
        return !MockCompanies.forCurrentUser().isEmpty();
    }

    private static void togglePersona(String name, Dialog dialog) {
        switch (name) {
            case "Guest" -> {
                if (AuthSession.isSignedIn() || AuthSession.isAdmin()) {
                    AuthSession.signOut();
                    MockCart.clear();
                    MockCompanies.clear();
                    MockSession.clearCurrentCompany();
                }
            }
            case "Member" -> {
                if (!AuthSession.isSignedIn()) {
                    AuthSession.signIn("adam");
                } else if (!hasAnyCompanyRole() && !AuthSession.isAdmin()) {
                    // Plain member with nothing else attached → sign out
                    AuthSession.signOut();
                    MockCart.clear();
                }
                // else: signed in with companies/admin attached — can't unsign
                // here; deselect those toggles first.
            }
            case "Manager", "Co-owner", "Founder" -> {
                if (hasRole(name)) {
                    String currentId = MockSession.currentCompanyId();
                    MockCompanies.forCurrentUser().removeIf(c -> name.equals(c.role()));
                    if (currentId != null
                        && MockCompanies.forCurrentUser().stream().noneMatch(c -> currentId.equals(c.id()))) {
                        MockSession.clearCurrentCompany();
                    }
                } else {
                    if (!AuthSession.isSignedIn()) AuthSession.signIn("adam");
                    String id = "demo-" + name.toLowerCase().replace("-", "");
                    MockCompanies.add(new MockCompanies.Company(
                        id, seededCompanyName(name),
                        "Seeded by dev panel.", "demo@ticketing.test",
                        name, "Active", "Founder".equals(name) ? 3 : 1, 5
                    ));
                    if ("Manager".equals(name)) {
                        MockPermissions.setAll(id, java.util.EnumSet.of(
                            Capability.VIEW_COMPANY_EVENTS,
                            Capability.RESPOND_INQUIRIES));
                    }
                    if (MockSession.currentCompanyId() == null) {
                        MockSession.setCurrentCompany(id);
                    }
                }
            }
            case "Admin" -> AuthSession.setAdmin(!AuthSession.isAdmin());
        }
        refresh(dialog);
    }

    private static String seededCompanyName(String role) {
        return switch (role) {
            case "Founder"  -> "Live Nation Israel";
            case "Co-owner" -> "Coca-Cola Arena";
            case "Manager"  -> "Shuni Productions";
            default          -> role + " demo company";
        };
    }

    // ---------------------------------------------------------------------
    // Identity — name + signed-in + admin
    // ---------------------------------------------------------------------

    private static Component buildIdentityRow() {
        TextField name = new TextField();
        name.setLabel("Display name");
        name.setValue(AuthSession.displayName() == null ? "" : AuthSession.displayName());
        name.setWidth("220px");

        Checkbox signedIn = new Checkbox("Signed in", AuthSession.isSignedIn());
        Checkbox isAdmin  = new Checkbox("Admin pool", AuthSession.isAdmin());

        signedIn.addValueChangeListener(e -> {
            if (e.getValue()) AuthSession.signIn(name.isEmpty() ? "adam" : name.getValue());
            else              AuthSession.signOut();
        });
        isAdmin.addValueChangeListener(e -> {
            if (e.getValue()) {
                AuthSession.signInAsAdmin(name.isEmpty() ? "admin" : name.getValue());
                MockCompanies.clear();
                MockSession.clearCurrentCompany();
            } else if (AuthSession.isAdmin()) {
                AuthSession.signIn(name.isEmpty() ? "adam" : name.getValue());
            }
        });
        name.addValueChangeListener(e -> {
            if (AuthSession.isAdmin())            AuthSession.signInAsAdmin(e.getValue());
            else if (AuthSession.isSignedIn())    AuthSession.signIn(e.getValue());
        });

        return hrow(16, name, signedIn, isAdmin);
    }

    // ---------------------------------------------------------------------
    // Companies — list, add by role, pick current
    // ---------------------------------------------------------------------

    private static Component buildCompaniesBlock(Dialog dialog) {
        Div block = vcol(8);

        List<MockCompanies.Company> all = MockCompanies.forCurrentUser();
        if (all.isEmpty()) {
            Span hint = new Span("No companies. Use a button below to add one.");
            hint.getStyle().set("color", SECTION_SUB_COLOR).set("font-size", "13px");
            block.add(hint);
        } else {
            String currentId = MockSession.currentCompanyId();
            for (MockCompanies.Company c : all) {
                boolean current = c.id().equals(currentId)
                    || (currentId == null && all.get(0).id().equals(c.id()));
                Span tag = new Span();
                tag.getStyle().set("flex", "1").set("font-size", "13.5px")
                    .set("min-width", "180px");
                tag.getElement().setProperty("innerHTML",
                    (current ? "<span style='color:#ca8a04'>★</span> " : "")
                    + "<b>" + escape(c.name()) + "</b>"
                    + " <span style='color:" + SECTION_SUB_COLOR + "'>· " + c.role() + "</span>");

                Button select = ghostBtn("Make current", () -> {
                    MockSession.setCurrentCompany(c.id());
                    refresh(dialog);
                });
                Button remove = dangerBtn("Remove", () -> {
                    MockCompanies.forCurrentUser().removeIf(x -> x.id().equals(c.id()));
                    if (c.id().equals(MockSession.currentCompanyId()))
                        MockSession.clearCurrentCompany();
                    refresh(dialog);
                });
                block.add(hrow(8, tag, select, remove));
            }
        }

        Div addRow = hrow(6,
            addCompanyBtn("+ Founder",  "Founder",  dialog),
            addCompanyBtn("+ Co-owner", "Co-owner", dialog),
            addCompanyBtn("+ Manager",  "Manager",  dialog)
        );
        addRow.getStyle().set("margin-top", "4px");
        block.add(addRow);

        return block;
    }

    private static Button addCompanyBtn(String label, String role, Dialog dialog) {
        return ghostBtn(label, () -> {
            if (!AuthSession.isSignedIn()) AuthSession.signIn("adam");
            String id = "dev-" + UUID.randomUUID().toString().substring(0, 8);
            MockCompanies.add(new MockCompanies.Company(
                id, role + " company",
                "Added from dev panel.", "demo@ticketing.test",
                role, "Active", 1, 0
            ));
            MockSession.setCurrentCompany(id);
            if ("Manager".equals(role)) {
                MockPermissions.setAll(id, java.util.EnumSet.of(
                    Capability.VIEW_COMPANY_EVENTS,
                    Capability.RESPOND_INQUIRIES));
            }
            refresh(dialog);
        });
    }

    // ---------------------------------------------------------------------
    // Manager permission grants
    // ---------------------------------------------------------------------

    private static String currentManagerSubtitle() {
        MockCompanies.Company current = MockSession.currentCompany();
        if (current == null) return "No current company — pick one above.";
        if (!"Manager".equals(current.role())) {
            return "Current role is " + current.role() + " — grants only apply to Managers.";
        }
        return "Toggle to grant/revoke. Founders & Co-owners hold every grantable cap automatically.";
    }

    private static Component buildManagerPermsBlock() {
        MockCompanies.Company current = MockSession.currentCompany();
        if (current == null || !"Manager".equals(current.role())) {
            return new Span();   // empty placeholder; subtitle carries the message
        }

        var held = MockPermissions.forCompany(current.id());
        Div grid = new Div();
        grid.getStyle().set("display", "grid")
            .set("grid-template-columns", "repeat(2, minmax(0, 1fr))")
            .set("gap", "4px 16px");

        for (Capability c : MockPermissions.GRANTABLE) {
            Checkbox cb = new Checkbox(prettify(c.name()), held.contains(c));
            cb.addValueChangeListener(e -> {
                if (e.getValue()) MockPermissions.grant(current.id(), c);
                else              MockPermissions.revoke(current.id(), c);
            });
            grid.add(cb);
        }
        return grid;
    }

    // ---------------------------------------------------------------------
    // Capability inspector — read-only badge cloud
    // ---------------------------------------------------------------------

    private static Component buildCapabilityCloud() {
        var caps = Capabilities.forCurrentUser();
        Div cloud = new Div();
        cloud.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "6px");

        if (caps.isEmpty()) {
            Span none = new Span("(none)");
            none.getStyle().set("color", SECTION_SUB_COLOR);
            cloud.add(none);
        } else {
            for (Capability c : caps) {
                cloud.add(new LkBadge(c.name(), LkBadge.Tone.primary).small());
            }
        }
        return cloud;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Close and re-open the dialog so every section reflects the new state. */
    private static void refresh(Dialog dialog) {
        dialog.close();
        show();
    }

    /** Title-case a SCREAMING_SNAKE_CASE name → "Screaming Snake Case". */
    private static String prettify(String snake) {
        String[] parts = snake.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
