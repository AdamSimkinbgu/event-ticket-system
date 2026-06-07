package com.ticketing.system.Presentation.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;

/**
 * Base class for every V2 placeholder view (V2-F-02).
 *
 * <p>Each concrete view extends this and:
 * <ol>
 *   <li>Calls {@code super(name, planId, owner, description)} to render the
 *       header (plan badge · title · owner · description).</li>
 *   <li>Calls {@code add(...)} with one or more of the {@code wire*} helpers
 *       to sketch out the intended UI as polished mockups.</li>
 * </ol>
 *
 * <p>Design tokens follow {@code docs/vaadin-standards.html}: primary
 * {@code #1a5490}, accent {@code #f47920}. Cards, forms, grids, and
 * buttons render as real-looking shells (white backgrounds, subtle
 * borders/shadows, skeleton bars) rather than dashed placeholders so the
 * page resembles the final UI even before wiring.
 *
 * <p>Layout uses CSS flex + grid with {@code min-width: 0} on flex
 * children and {@code flex-wrap: wrap} on rows, so the view rescales
 * gracefully from full-width desktop down to phone-narrow without
 * horizontal overflow. The root deliberately does <b>not</b> call
 * {@code setSizeFull()} so AppLayout's main scroll handles overflow
 * vertically.
 */
public abstract class PlaceholderView extends VerticalLayout {

    protected PlaceholderView(String name, String planId, String owner, String description) {
        setWidthFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);
        getStyle()
            .set("max-width", "1280px")
            .set("width", "100%")
            .set("margin", "0 auto")
            .set("padding", "32px clamp(16px, 4vw, 48px) 80px")
            .set("box-sizing", "border-box")
            .set("gap", "20px")
            .set("display", "flex")
            .set("flex-direction", "column");

        add(buildPageHeader(name, planId, owner, description));
    }

    /**
     * Clean page header — H1 title with a tiny "ℹ" dot whose hover tooltip
     * surfaces the plan ID, owner, and description for the dev team. End
     * users only see the title; dev framing is one hover away.
     */
    private Component buildPageHeader(String name, String planId, String owner, String description) {
        H1 title = new H1(name);
        title.getStyle()
            .set("margin", "0")
            .set("color", "#0f172a")
            .set("font-size", "clamp(1.6rem, 3vw, 2.4rem)")
            .set("font-weight", "700")
            .set("line-height", "1.15")
            .set("letter-spacing", "-0.02em")
            .set("display", "inline-flex")
            .set("align-items", "baseline")
            .set("gap", "10px");

        Span info = new Span("ℹ");
        info.getElement().setAttribute("title",
            planId + "  ·  Owner: " + owner + "\n\n" + description);
        info.getStyle()
            .set("color", "#cbd5e1")
            .set("font-size", "0.85rem")
            .set("cursor", "help")
            .set("vertical-align", "middle");
        title.add(info);
        return title;
    }

    // ---------------------------------------------------------------------
    // Wireframe helpers
    // ---------------------------------------------------------------------

    /** Soft pill of inline text — useful inside cards as a content row. */
    protected static Component wireBox(String label) {
        Div box = new Div();
        box.setText(label);
        box.getStyle()
            .set("background", "#f1f5f9")
            .set("color", "#334155")
            .set("padding", "10px 14px")
            .set("border-radius", "8px")
            .set("font-size", "0.875rem")
            .set("border", "1px solid #e2e8f0")
            .set("min-width", "0")
            .set("box-sizing", "border-box");
        return box;
    }

    /** White card with title bar, padded body. */
    protected static Component wireCard(String title, Component... children) {
        Div header = new Div();
        Span ttl = new Span(title);
        ttl.getStyle()
            .set("color", "#1a5490")
            .set("font-size", "0.95rem")
            .set("font-weight", "600");
        header.add(ttl);
        header.getStyle()
            .set("padding", "14px 20px")
            .set("border-bottom", "1px solid #f1f5f9")
            .set("background", "#fafbfc");

        Div body = new Div();
        body.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "12px")
            .set("padding", "20px");
        for (Component c : children) body.add(c);

        Div card = new Div();
        card.add(header);
        card.add(body);
        card.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "12px")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.04)")
            .set("overflow", "hidden")
            .set("box-sizing", "border-box")
            .set("width", "100%")
            .set("min-width", "0");
        return card;
    }

    /** Section heading between cards. */
    protected static Component wireSectionTitle(String title) {
        H2 h = new H2(title);
        h.getStyle()
            .set("color", "#0f172a")
            .set("font-size", "1.1rem")
            .set("font-weight", "700")
            .set("margin", "12px 0 0");
        return h;
    }

    /** Responsive row — children wrap onto new lines on narrow screens. */
    protected static Component wireRow(Component... items) {
        Div row = new Div();
        row.getStyle()
            .set("display", "flex")
            .set("flex-wrap", "wrap")
            .set("gap", "16px")
            .set("width", "100%")
            .set("align-items", "stretch");
        for (Component c : items) {
            c.getElement().getStyle().set("flex", "1 1 220px").set("min-width", "0");
            row.add(c);
        }
        return row;
    }

    /** Vertical stack. */
    protected static Component wireColumn(Component... items) {
        Div col = new Div();
        col.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "16px")
            .set("width", "100%")
            .set("min-width", "0");
        for (Component c : items) col.add(c);
        return col;
    }

    /** 2-pane split. Wraps to stacked on narrow. */
    protected static Component wireSplit(int leftFlex, int rightFlex, Component left, Component right) {
        left.getElement().getStyle()
            .set("flex", leftFlex + " 1 260px")
            .set("min-width", "0");
        right.getElement().getStyle()
            .set("flex", rightFlex + " 1 260px")
            .set("min-width", "0");
        Div split = new Div();
        split.add(left);
        split.add(right);
        split.getStyle()
            .set("display", "flex")
            .set("flex-wrap", "wrap")
            .set("gap", "16px")
            .set("align-items", "flex-start")
            .set("width", "100%");
        return split;
    }

    /** Filter bar — horizontal row of dropdown-style chips. */
    protected static Component wireFilterBar(String... filterLabels) {
        Div bar = new Div();
        bar.getStyle()
            .set("display", "flex")
            .set("flex-wrap", "wrap")
            .set("gap", "8px")
            .set("padding", "12px 16px")
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "10px")
            .set("width", "100%")
            .set("box-sizing", "border-box");
        for (String label : filterLabels) {
            Span chip = new Span(label + "  ▾");
            chip.getStyle()
                .set("background", "#f8fafc")
                .set("border", "1px solid #e2e8f0")
                .set("color", "#334155")
                .set("padding", "6px 12px")
                .set("border-radius", "8px")
                .set("font-size", "0.85rem")
                .set("cursor", "pointer")
                .set("white-space", "nowrap");
            bar.add(chip);
        }
        return bar;
    }

    /** Filter sidebar — card with checkbox-style option rows. */
    protected static Component wireFilterSidebar(String... filterGroups) {
        Div side = new Div();
        side.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "12px")
            .set("padding", "20px")
            .set("box-sizing", "border-box")
            .set("width", "100%")
            .set("min-width", "0")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04)");

        Span title = new Span("Filters");
        title.getStyle()
            .set("color", "#0f172a")
            .set("font-weight", "700")
            .set("font-size", "1rem")
            .set("display", "block")
            .set("margin-bottom", "12px");
        side.add(title);

        for (String group : filterGroups) {
            Span lbl = new Span(group);
            lbl.getStyle()
                .set("color", "#475569")
                .set("font-size", "0.72rem")
                .set("font-weight", "700")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "1px")
                .set("display", "block")
                .set("margin", "16px 0 8px");
            side.add(lbl);
            for (int i = 0; i < 3; i++) {
                Span opt = new Span((i == 0 ? "☑" : "☐") + "  " + group + " option " + (i + 1));
                opt.getStyle()
                    .set("color", "#334155")
                    .set("font-size", "0.875rem")
                    .set("display", "block")
                    .set("padding", "4px 0")
                    .set("cursor", "pointer");
                side.add(opt);
            }
        }
        return side;
    }

    /** Polished grid: header bar + 4 skeleton rows (gray bars of varying widths). */
    protected static Component wireGrid(String... columnHeaders) {
        Div table = new Div();
        table.getStyle()
            .set("display", "table")
            .set("width", "100%")
            .set("border-collapse", "collapse");

        Div headerRow = new Div();
        headerRow.getStyle()
            .set("display", "table-row")
            .set("background", "#f8fafc");
        for (String h : columnHeaders) {
            Div cell = new Div();
            cell.setText(h);
            cell.getStyle()
                .set("display", "table-cell")
                .set("color", "#475569")
                .set("font-weight", "700")
                .set("font-size", "0.72rem")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "1px")
                .set("padding", "12px 16px")
                .set("border-bottom", "1px solid #e2e8f0")
                .set("white-space", "nowrap");
            headerRow.add(cell);
        }
        table.add(headerRow);

        String[] widths = {"78%", "55%", "88%", "48%", "70%", "62%", "82%"};
        for (int r = 0; r < 4; r++) {
            Div row = new Div();
            row.getStyle()
                .set("display", "table-row")
                .set("background", r % 2 == 0 ? "white" : "#fafbfc");
            for (int c = 0; c < columnHeaders.length; c++) {
                Div bar = new Div();
                bar.getStyle()
                    .set("background", "#e2e8f0")
                    .set("height", "10px")
                    .set("border-radius", "999px")
                    .set("width", widths[(r * 3 + c) % widths.length]);
                Div cell = new Div();
                cell.add(bar);
                cell.getStyle()
                    .set("display", "table-cell")
                    .set("padding", "16px")
                    .set("border-bottom", r < 3 ? "1px solid #f1f5f9" : "none")
                    .set("vertical-align", "middle");
                row.add(cell);
            }
            table.add(row);
        }

        Div outer = new Div();
        outer.add(table);
        outer.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "12px")
            .set("overflow", "auto")
            .set("width", "100%")
            .set("min-width", "0")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04)")
            .set("box-sizing", "border-box");
        return outer;
    }

    /** Form skeleton — labels + real-looking input fields. */
    protected static Component wireForm(String... fieldLabels) {
        Div form = new Div();
        form.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 240px), 1fr))")
            .set("gap", "16px")
            .set("width", "100%")
            .set("min-width", "0");
        for (String label : fieldLabels) {
            Span lbl = new Span(label);
            lbl.getStyle()
                .set("color", "#334155")
                .set("font-size", "0.85rem")
                .set("font-weight", "600")
                .set("display", "block")
                .set("margin-bottom", "6px");
            Div input = new Div();
            input.setText("Enter " + label.toLowerCase().replaceAll("[^a-z ]", "").trim() + "…");
            input.getStyle()
                .set("background", "white")
                .set("border", "1px solid #cbd5e1")
                .set("border-radius", "8px")
                .set("padding", "10px 14px")
                .set("color", "#94a3b8")
                .set("font-size", "0.9rem")
                .set("min-height", "20px")
                .set("box-shadow", "inset 0 1px 2px rgba(15, 23, 42, 0.03)");
            Div field = new Div();
            field.add(lbl);
            field.add(input);
            field.getStyle().set("min-width", "0");
            form.add(field);
        }
        return form;
    }

    /** Action row — primary on right, secondaries to its left. */
    protected static Component wireActions(String primary, String... secondary) {
        Div row = new Div();
        row.getStyle()
            .set("display", "flex")
            .set("flex-wrap", "wrap")
            .set("gap", "8px")
            .set("justify-content", "flex-end")
            .set("margin-top", "8px")
            .set("width", "100%");
        for (String s : secondary) {
            Span btn = new Span(s);
            btn.getStyle()
                .set("background", "white")
                .set("color", "#334155")
                .set("border", "1px solid #cbd5e1")
                .set("padding", "9px 18px")
                .set("border-radius", "8px")
                .set("font-size", "0.88rem")
                .set("font-weight", "600")
                .set("cursor", "pointer");
            row.add(btn);
        }
        Span main = new Span(primary);
        main.getStyle()
            .set("background", "#1a5490")
            .set("color", "white")
            .set("border", "1px solid #1a5490")
            .set("padding", "9px 22px")
            .set("border-radius", "8px")
            .set("font-size", "0.88rem")
            .set("font-weight", "600")
            .set("cursor", "pointer")
            .set("box-shadow", "0 1px 2px rgba(26, 84, 144, 0.20)");
        row.add(main);
        return row;
    }

    /** Countdown chip — amber pill. */
    protected static Component wireCountdown(String mockTime) {
        Span chip = new Span("⏱ " + mockTime);
        chip.getStyle()
            .set("background", "#fff7ed")
            .set("color", "#9a3412")
            .set("padding", "5px 12px")
            .set("border-radius", "999px")
            .set("font-family", "'Menlo', monospace")
            .set("font-size", "0.85rem")
            .set("font-weight", "700")
            .set("border", "1px solid #fed7aa")
            .set("display", "inline-block");
        return chip;
    }

    /** Status badge — variant: "success" / "warn" / "error" / "muted". */
    protected static Component wireBadge(String text, String variant) {
        String bg, color;
        switch (variant) {
            case "success" -> { bg = "#dcfce7"; color = "#15803d"; }
            case "warn"    -> { bg = "#fef3c7"; color = "#b45309"; }
            case "error"   -> { bg = "#fee2e2"; color = "#b91c1c"; }
            default        -> { bg = "#f1f5f9"; color = "#475569"; }
        }
        Span badge = new Span(text);
        badge.getStyle()
            .set("background", bg)
            .set("color", color)
            .set("padding", "3px 10px")
            .set("border-radius", "6px")
            .set("font-size", "0.72rem")
            .set("font-weight", "700")
            .set("text-transform", "uppercase")
            .set("letter-spacing", "0.5px")
            .set("display", "inline-block");
        return badge;
    }

    /**
     * Card grid — auto-fit responsive, used by dashboard views to show
     * navigation cards. Wraps to a single column on narrow.
     */
    protected static Component wireCardGrid(Component... cards) {
        Div grid = new Div();
        grid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 260px), 1fr))")
            .set("gap", "16px")
            .set("width", "100%");
        for (Component c : cards) grid.add(c);
        return grid;
    }

    /**
     * Navigation card — a clickable card that routes to {@code target}.
     * Used by dashboard hubs (Admin / Owner workspace) so every view is
     * reachable without typing a URL.
     */
    protected static Component wireNavCard(String emoji, String title, String description, Class<? extends Component> target) {
        Span e = new Span(emoji);
        e.getStyle()
            .set("font-size", "1.8rem")
            .set("display", "block")
            .set("margin-bottom", "10px");

        Span t = new Span(title);
        t.getStyle()
            .set("color", "#0f172a")
            .set("font-weight", "700")
            .set("font-size", "1rem")
            .set("display", "block")
            .set("margin-bottom", "4px");

        Span d = new Span(description);
        d.getStyle()
            .set("color", "#64748b")
            .set("font-size", "0.82rem")
            .set("display", "block")
            .set("line-height", "1.5");

        Span arrow = new Span("Open  →");
        arrow.getStyle()
            .set("color", "#1a5490")
            .set("font-size", "0.8rem")
            .set("font-weight", "600")
            .set("display", "block")
            .set("margin-top", "12px");

        Div content = new Div();
        content.add(e, t, d, arrow);

        RouterLink link = new RouterLink("", target);
        link.removeAll();
        link.add(content);
        link.getStyle()
            .set("display", "block")
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "12px")
            .set("padding", "20px")
            .set("text-decoration", "none")
            .set("color", "inherit")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.04)")
            .set("cursor", "pointer")
            .set("box-sizing", "border-box")
            .set("min-width", "0");
        return link;
    }

    /**
     * Secondary navigation link — real {@link RouterLink} centred under a form
     * (e.g. "Don't have an account? Create one →" navigating to RegisterView).
     */
    protected static Component wireSecondaryLink(String label, Class<? extends Component> target) {
        RouterLink link = new RouterLink(label, target);
        link.getStyle()
            .set("color", "#1a5490")
            .set("font-size", "0.85rem")
            .set("text-align", "center")
            .set("display", "block")
            .set("padding", "12px 4px 4px")
            .set("cursor", "pointer")
            .set("font-weight", "500")
            .set("text-decoration", "underline")
            .set("text-decoration-color", "rgba(26, 84, 144, 0.3)")
            .set("text-underline-offset", "3px");
        return link;
    }

    /**
     * Org-chart person card — colored avatar circle, name, role badge, optional
     * subline (e.g. "appointed by Alice · 2025-01-08"). Use with
     * {@link #wireOrgSubtree} to render a recursive appointment tree.
     *
     * @param roleVariant one of {@code "founder"}, {@code "owner"},
     *                    {@code "manager"} (default: muted slate)
     */
    protected static Component wireOrgPerson(String initial, String name, String role, String roleVariant, String subline) {
        String accent, badgeBg, badgeFg;
        switch (roleVariant) {
            case "founder" -> { accent = "#ca8a04"; badgeBg = "#fef3c7"; badgeFg = "#a16207"; }
            case "owner"   -> { accent = "#1a5490"; badgeBg = "#dbeafe"; badgeFg = "#1a5490"; }
            case "manager" -> { accent = "#0e7a5d"; badgeBg = "#dcfce7"; badgeFg = "#15803d"; }
            default        -> { accent = "#475569"; badgeBg = "#f1f5f9"; badgeFg = "#475569"; }
        }

        Div avatar = new Div();
        avatar.setText(initial);
        avatar.getStyle()
            .set("background", accent)
            .set("color", "white")
            .set("width", "40px")
            .set("height", "40px")
            .set("min-width", "40px")
            .set("border-radius", "50%")
            .set("display", "flex")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("font-weight", "700")
            .set("font-size", "1rem")
            .set("flex-shrink", "0");

        Span nameSpan = new Span(name);
        nameSpan.getStyle()
            .set("color", "#0f172a")
            .set("font-weight", "700")
            .set("font-size", "0.95rem")
            .set("display", "block");

        Span roleBadge = new Span(role);
        roleBadge.getStyle()
            .set("background", badgeBg)
            .set("color", badgeFg)
            .set("padding", "2px 9px")
            .set("border-radius", "999px")
            .set("font-size", "0.68rem")
            .set("font-weight", "700")
            .set("text-transform", "uppercase")
            .set("letter-spacing", "0.5px")
            .set("display", "inline-block")
            .set("margin-top", "3px");

        Div col = new Div();
        col.add(nameSpan, roleBadge);
        if (subline != null && !subline.isEmpty()) {
            Span sub = new Span(subline);
            sub.getStyle()
                .set("color", "#64748b")
                .set("font-size", "0.78rem")
                .set("display", "block")
                .set("margin-top", "6px")
                .set("line-height", "1.4");
            col.add(sub);
        }
        col.getStyle().set("min-width", "0").set("flex", "1 1 auto");

        Div card = new Div();
        card.add(avatar, col);
        card.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-left", "4px solid " + accent)
            .set("border-radius", "10px")
            .set("padding", "12px 16px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "14px")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04)")
            .set("max-width", "440px")
            .set("box-sizing", "border-box");
        return card;
    }

    /**
     * Org-chart subtree — wraps a person card and recursively nests children
     * underneath, connected by a vertical guide line. Pass child subtrees
     * (themselves {@code wireOrgSubtree} calls) for nested levels.
     */
    protected static Component wireOrgSubtree(Component personCard, Component... children) {
        Div wrap = new Div();
        wrap.add(personCard);
        wrap.getStyle().set("min-width", "0");

        if (children.length > 0) {
            Div childContainer = new Div();
            childContainer.getStyle()
                .set("margin", "8px 0 4px 20px")
                .set("padding-left", "20px")
                .set("border-left", "2px solid #cbd5e1")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "10px");
            for (Component c : children) childContainer.add(c);
            wrap.add(childContainer);
        }
        return wrap;
    }

    /**
     * Policy composite node (AND/OR). Tinted container with a colored operator
     * badge and a "All of…" / "Any of…" description. Children are stacked
     * vertically inside with their own connector indent.
     */
    protected static Component wirePolicyOp(String op, Component... children) {
        boolean isAnd = "AND".equalsIgnoreCase(op);
        String bg     = isAnd ? "#eff6ff" : "#faf5ff";
        String border = isAnd ? "#bfdbfe" : "#e9d5ff";
        String chipBg = isAnd ? "#1a5490" : "#7c3aed";
        String desc   = isAnd ? "All of the following must hold" : "Any of the following must hold";

        Span badge = new Span(op.toUpperCase());
        badge.getStyle()
            .set("background", chipBg)
            .set("color", "white")
            .set("padding", "3px 14px")
            .set("border-radius", "999px")
            .set("font-size", "0.72rem")
            .set("font-weight", "700")
            .set("letter-spacing", "1.2px")
            .set("display", "inline-block");

        Span descSpan = new Span(desc);
        descSpan.getStyle()
            .set("color", "#475569")
            .set("font-size", "0.82rem")
            .set("margin-left", "12px");

        Span actions = new Span("+ rule    – remove");
        actions.getStyle()
            .set("color", "#94a3b8")
            .set("font-size", "0.72rem")
            .set("margin-left", "auto")
            .set("font-family", "'Menlo', monospace");

        Div header = new Div();
        header.add(badge, descSpan, actions);
        header.getStyle()
            .set("display", "flex")
            .set("align-items", "center")
            .set("flex-wrap", "wrap")
            .set("gap", "4px");

        Div body = new Div();
        body.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "8px")
            .set("margin-top", "12px")
            .set("padding-left", "12px")
            .set("border-left", "2px solid " + border);
        for (Component c : children) body.add(c);

        Div container = new Div();
        container.add(header, body);
        container.getStyle()
            .set("background", bg)
            .set("border", "1px solid " + border)
            .set("border-radius", "10px")
            .set("padding", "14px 16px")
            .set("box-sizing", "border-box")
            .set("min-width", "0");
        return container;
    }

    /**
     * Leaf policy rule (AgeAtLeast, QuantityAtMost, …). Renders as a compact
     * card with the rule type chip and the expression in monospace, plus a
     * drag handle on the left.
     */
    protected static Component wirePolicyLeaf(String ruleType, String expression) {
        Span handle = new Span("⋮⋮");
        handle.getStyle()
            .set("color", "#cbd5e1")
            .set("font-size", "1rem")
            .set("margin-right", "10px")
            .set("cursor", "grab")
            .set("flex-shrink", "0");

        Span chip = new Span(ruleType);
        chip.getStyle()
            .set("background", "#dcfce7")
            .set("color", "#15803d")
            .set("padding", "3px 10px")
            .set("border-radius", "5px")
            .set("font-size", "0.72rem")
            .set("font-weight", "700")
            .set("letter-spacing", "0.3px")
            .set("font-family", "'Menlo', monospace")
            .set("flex-shrink", "0");

        Span expr = new Span(expression);
        expr.getStyle()
            .set("color", "#0f172a")
            .set("font-size", "0.88rem")
            .set("margin-left", "12px")
            .set("font-family", "'Menlo', monospace")
            .set("flex", "1 1 auto")
            .set("min-width", "0");

        Span remove = new Span("✕");
        remove.getStyle()
            .set("color", "#cbd5e1")
            .set("font-size", "0.85rem")
            .set("margin-left", "10px")
            .set("cursor", "pointer")
            .set("flex-shrink", "0");

        Div card = new Div();
        card.add(handle, chip, expr, remove);
        card.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-left", "4px solid #0e7a5d")
            .set("border-radius", "8px")
            .set("padding", "10px 14px")
            .set("display", "flex")
            .set("align-items", "center")
            .set("flex-wrap", "wrap")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04)")
            .set("min-width", "0")
            .set("box-sizing", "border-box");
        return card;
    }

    /**
     * Palette item shown in the policy editor's "drag onto tree" rail.
     * Dashed border conveys "draggable source" affordance.
     */
    protected static Component wirePolicyPaletteItem(String label, String hint) {
        Span lbl = new Span(label);
        lbl.getStyle()
            .set("color", "#0f172a")
            .set("font-weight", "600")
            .set("font-size", "0.85rem")
            .set("font-family", "'Menlo', monospace")
            .set("display", "block");
        Span h = new Span(hint);
        h.getStyle()
            .set("color", "#64748b")
            .set("font-size", "0.75rem")
            .set("display", "block")
            .set("margin-top", "2px");
        Div card = new Div();
        card.add(lbl, h);
        card.getStyle()
            .set("background", "#fafbfc")
            .set("border", "1px dashed #cbd5e1")
            .set("border-radius", "8px")
            .set("padding", "8px 12px")
            .set("cursor", "grab")
            .set("min-width", "0");
        return card;
    }

    /**
     * Real-data grid — like {@link #wireGrid} but rows contain actual mock
     * content (event names, dates, prices) instead of skeleton bars. Used
     * by views that want to feel like the working product, not a loading
     * state.
     */
    protected static Component wireDataGrid(String[] headers, String[]... rows) {
        Div table = new Div();
        table.getStyle()
            .set("display", "table")
            .set("width", "100%")
            .set("border-collapse", "collapse");

        Div headerRow = new Div();
        headerRow.getStyle().set("display", "table-row").set("background", "#f8fafc");
        for (String h : headers) {
            Div cell = new Div();
            cell.setText(h);
            cell.getStyle()
                .set("display", "table-cell")
                .set("color", "#475569")
                .set("font-weight", "700")
                .set("font-size", "0.72rem")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "1px")
                .set("padding", "12px 16px")
                .set("border-bottom", "1px solid #e2e8f0")
                .set("white-space", "nowrap");
            headerRow.add(cell);
        }
        table.add(headerRow);

        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r];
            Div tr = new Div();
            tr.getStyle()
                .set("display", "table-row")
                .set("background", r % 2 == 0 ? "white" : "#fafbfc");
            for (int c = 0; c < row.length; c++) {
                Div cell = new Div();
                cell.setText(row[c]);
                cell.getStyle()
                    .set("display", "table-cell")
                    .set("padding", "14px 16px")
                    .set("border-bottom", r < rows.length - 1 ? "1px solid #f1f5f9" : "none")
                    .set("vertical-align", "middle")
                    .set("color", c == 0 ? "#0f172a" : "#334155")
                    .set("font-weight", c == 0 ? "600" : "400")
                    .set("font-size", "0.88rem");
                tr.add(cell);
            }
            table.add(tr);
        }

        Div outer = new Div();
        outer.add(table);
        outer.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "12px")
            .set("overflow", "auto")
            .set("width", "100%")
            .set("min-width", "0")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04)")
            .set("box-sizing", "border-box");
        return outer;
    }

    /**
     * Event poster card — gradient image-block (color-keyed to category),
     * category chip overlay, title, venue/date subline, price. Looks like a
     * real event listing card, not a wireframe.
     */
    protected static Component wireEventPoster(String category, String title, String venueDate, String price) {
        String[] gradient = switch (category.toLowerCase()) {
            case "concert"    -> new String[]{"#8b5cf6", "#ec4899"};
            case "sport"      -> new String[]{"#0ea5e9", "#10b981"};
            case "theatre"    -> new String[]{"#dc2626", "#f59e0b"};
            case "conference" -> new String[]{"#0d9488", "#1d4ed8"};
            default           -> new String[]{"#475569", "#1a5490"};
        };

        Span categoryBadge = new Span(category.toUpperCase());
        categoryBadge.getStyle()
            .set("background", "rgba(0, 0, 0, 0.35)")
            .set("color", "white")
            .set("padding", "4px 10px")
            .set("border-radius", "999px")
            .set("font-size", "0.65rem")
            .set("font-weight", "700")
            .set("letter-spacing", "1px")
            .set("backdrop-filter", "blur(4px)");

        Div imageBlock = new Div();
        imageBlock.add(categoryBadge);
        imageBlock.getStyle()
            .set("background", "linear-gradient(135deg, " + gradient[0] + " 0%, " + gradient[1] + " 100%)")
            .set("height", "150px")
            .set("display", "flex")
            .set("align-items", "flex-end")
            .set("padding", "14px")
            .set("color", "white");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
            .set("color", "#0f172a")
            .set("font-size", "1.05rem")
            .set("font-weight", "700")
            .set("line-height", "1.3")
            .set("display", "block");

        Span venueDateSpan = new Span(venueDate);
        venueDateSpan.getStyle()
            .set("color", "#64748b")
            .set("font-size", "0.82rem")
            .set("display", "block")
            .set("margin-top", "4px");

        Span priceSpan = new Span(price);
        priceSpan.getStyle()
            .set("color", "#1a5490")
            .set("font-weight", "700")
            .set("font-size", "0.92rem")
            .set("display", "block")
            .set("margin-top", "10px");

        Div body = new Div();
        body.add(titleSpan, venueDateSpan, priceSpan);
        body.getStyle()
            .set("padding", "14px 16px 16px")
            .set("display", "flex")
            .set("flex-direction", "column");

        Div card = new Div();
        card.add(imageBlock, body);
        card.getStyle()
            .set("background", "white")
            .set("border", "1px solid #e2e8f0")
            .set("border-radius", "12px")
            .set("overflow", "hidden")
            .set("box-shadow", "0 1px 2px rgba(15, 23, 42, 0.04), 0 6px 16px rgba(15, 23, 42, 0.06)")
            .set("min-width", "0")
            .set("box-sizing", "border-box")
            .set("cursor", "pointer");
        return card;
    }

    /**
     * Category chip row — pill buttons for browse-by-category. The first
     * label is rendered as "active" (filled), the rest as outlined.
     */
    protected static Component wireCategoryChips(String... labels) {
        Div row = new Div();
        row.getStyle()
            .set("display", "flex")
            .set("flex-wrap", "wrap")
            .set("gap", "8px");
        for (int i = 0; i < labels.length; i++) {
            boolean active = i == 0;
            Span chip = new Span(labels[i]);
            chip.getStyle()
                .set("background", active ? "#1a5490" : "white")
                .set("color", active ? "white" : "#334155")
                .set("border", "1px solid " + (active ? "#1a5490" : "#e2e8f0"))
                .set("padding", "8px 16px")
                .set("border-radius", "999px")
                .set("font-size", "0.88rem")
                .set("font-weight", "600")
                .set("cursor", "pointer")
                .set("white-space", "nowrap");
            row.add(chip);
        }
        return row;
    }

    /**
     * Landing-page hero — large gradient banner with title, subline, and a
     * prominent inline search box. Used at the top of catalogue / browse
     * pages.
     */
    protected static Component wireHeroSearch(String title, String subtitle, String searchPlaceholder) {
        Span t = new Span(title);
        t.getStyle()
            .set("color", "white")
            .set("font-size", "clamp(1.8rem, 4vw, 2.8rem)")
            .set("font-weight", "700")
            .set("display", "block")
            .set("line-height", "1.1")
            .set("letter-spacing", "-0.02em");
        Span s = new Span(subtitle);
        s.getStyle()
            .set("color", "rgba(255, 255, 255, 0.88)")
            .set("font-size", "clamp(0.95rem, 1.5vw, 1.1rem)")
            .set("display", "block")
            .set("margin-top", "10px")
            .set("max-width", "560px");

        Div searchBox = new Div();
        searchBox.setText("🔍   " + searchPlaceholder);
        searchBox.getStyle()
            .set("background", "white")
            .set("border-radius", "12px")
            .set("padding", "16px 20px")
            .set("color", "#94a3b8")
            .set("font-size", "1rem")
            .set("margin-top", "28px")
            .set("max-width", "640px")
            .set("box-shadow", "0 4px 16px rgba(0, 0, 0, 0.12)")
            .set("cursor", "text");

        Div hero = new Div();
        hero.add(t, s, searchBox);
        hero.getStyle()
            .set("background", "linear-gradient(135deg, #1a5490 0%, #2c7bb6 55%, #f47920 200%)")
            .set("border-radius", "16px")
            .set("padding", "44px clamp(24px, 4vw, 56px)")
            .set("box-shadow", "0 8px 24px rgba(26, 84, 144, 0.18)")
            .set("box-sizing", "border-box")
            .set("width", "100%");
        return hero;
    }

    /** Hero banner — full-width gradient with title + subtitle. */
    protected static Component wireHero(String title, String subtitle) {
        Span t = new Span(title);
        t.getStyle()
            .set("color", "white")
            .set("font-size", "clamp(1.4rem, 3vw, 1.9rem)")
            .set("font-weight", "700")
            .set("display", "block")
            .set("line-height", "1.2")
            .set("letter-spacing", "-0.01em");
        Span s = new Span(subtitle);
        s.getStyle()
            .set("color", "rgba(255,255,255,0.85)")
            .set("font-size", "0.95rem")
            .set("display", "block")
            .set("margin-top", "8px");

        Div hero = new Div();
        hero.add(t);
        hero.add(s);
        hero.getStyle()
            .set("background", "linear-gradient(135deg, #1a5490 0%, #2c7bb6 60%, #f47920 200%)")
            .set("border-radius", "12px")
            .set("padding", "28px clamp(20px, 4vw, 36px)")
            .set("box-shadow", "0 4px 12px rgba(26, 84, 144, 0.15)")
            .set("width", "100%")
            .set("box-sizing", "border-box");
        return hero;
    }
}
