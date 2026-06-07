package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

/**
 * Top app bar — slot-based fluent API. Compose by chaining
 * {@link #brand(String)}, {@link #nav}, {@link #searchDefault()},
 * {@link #bellDefault(boolean)}, {@link #account}, etc. Items render
 * in call order (DOM order); the right slot is auto-margined to the
 * right edge by the kit's {@code .lk-topbar-right} CSS.
 *
 * <p>Three shell variants — {@link Variant#MAIN} (buyer, blue),
 * {@link Variant#ADMIN} (organizer crumb, blue), {@link Variant#PLATFORM}
 * (system admin, orange).
 */
public class LkTopBar extends Header {

    public enum Variant { MAIN, ADMIN, PLATFORM }
    public record NavItem(String label, Class<? extends Component> target) { }

    private final Variant variant;
    private final Span rightSlot = new Span();
    private boolean rightAttached;

    public LkTopBar(Variant variant) {
        this.variant = variant;
        addClassName("lk-topbar");
        if (variant == Variant.PLATFORM) addClassName("lk-topbar-admin");
        getStyle().set("width", "100%").set("box-sizing", "border-box");
        rightSlot.addClassName("lk-topbar-right");
    }

    // ---------------------------------------------------------------------
    // Left / centre slots
    // ---------------------------------------------------------------------

    public LkTopBar brand(String text) { return brand(text, null); }

    public LkTopBar brand(String text, String crumb) {
        Span brand = new Span();
        brand.addClassName("lk-brand");
        brand.add(new LkIcon("ticket", 20));
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(text) + "</b>");
        brand.add(name);
        if (crumb != null) {
            Span c = new Span(crumb);
            c.addClassName("lk-brand-crumb");
            brand.add(c);
        }
        add(brand);
        return this;
    }

    public LkTopBar nav(List<NavItem> items, String activeLabel) {
        Span nav = new Span();
        nav.addClassName("lk-topnav");
        for (NavItem item : items) {
            if (item.target != null) {
                RouterLink link = new RouterLink(item.label, item.target);
                link.addClassName("lk-topnav-link");
                if (item.label.equals(activeLabel)) link.addClassName("on");
                nav.add(link);
            } else {
                Span a = new Span(item.label);
                a.addClassName("lk-topnav-link");
                if (item.label.equals(activeLabel)) a.addClassName("on");
                nav.add(a);
            }
        }
        add(nav);
        return this;
    }

    public LkTopBar search(Component panel) {
        Span trigger = new Span();
        trigger.addClassName("lk-topbar-search");
        trigger.add(new LkIcon("search", 16));
        Span txt = new Span("Search events, artists, venues…");
        txt.addClassName("lk-search-txt");
        trigger.add(txt);
        LkPopover pop = new LkPopover(trigger, panel)
            .position(PopoverPosition.BOTTOM_END).width("392px");
        pop.addClassName("lk-search-wrap");
        add(pop);
        return this;
    }

    public LkTopBar searchDefault() { return search(LkSearchPanel.defaults()); }

    /** Add a single right-aligned text/icon link (e.g. "← Back to site"). */
    public LkTopBar rightLink(String label, String iconName, Class<? extends Component> target) {
        Span spacer = new Span();
        spacer.getStyle().set("flex", "1");
        add(spacer);
        if (target != null) {
            RouterLink link = new RouterLink();
            link.setRoute(target);
            decorateRightLink(link, label, iconName);
            add(link);
        } else {
            Span a = new Span();
            decorateRightLink(a, label, iconName);
            add(a);
        }
        return this;
    }

    private static void decorateRightLink(Component link, String label, String iconName) {
        link.getElement().getClassList().add("lk-topnav-link");
        link.getElement().getClassList().add("on");
        link.getElement().getStyle()
            .set("opacity", ".92")
            .set("display", "inline-flex")
            .set("align-items", "center")
            .set("gap", "5px");
        if (iconName != null) link.getElement().appendChild(new LkIcon(iconName, 14).getElement());
        link.getElement().appendChild(new Span(label).getElement());
    }

    // ---------------------------------------------------------------------
    // Right slot — bell / account / guest actions
    // ---------------------------------------------------------------------

    public LkTopBar bell(Component panel, String badgeCount, String ariaLabel) {
        ensureRightSlot();
        NativeButton bell = new NativeButton();
        bell.addClassName("lk-bell");
        bell.getElement().setAttribute("aria-label", ariaLabel == null ? "Notifications" : ariaLabel);
        bell.add(new LkIcon("bell", 18));
        if (badgeCount != null && !badgeCount.isEmpty()) {
            Span badge = new Span(badgeCount);
            badge.addClassName("lk-bell-badge");
            bell.add(badge);
        }
        LkPopover pop = new LkPopover(bell, panel).position(PopoverPosition.BOTTOM_END);
        rightSlot.add(pop);
        return this;
    }

    public LkTopBar bellDefault(boolean adminVariant) {
        return bell(adminVariant ? LkNotifPanel.admin() : LkNotifPanel.buyer(),
                    adminVariant ? "5" : "3",
                    adminVariant ? "Admin alerts" : "Notifications");
    }

    public LkTopBar account(String initials, String tooltip, Component menu) {
        return account(initials, tooltip, menu, null, null);
    }

    public LkTopBar account(String initials, String tooltip, Component menu, String bg, String fg) {
        ensureRightSlot();
        NativeButton avatar = new NativeButton(initials);
        avatar.addClassName("lk-avatar");
        if (bg != null) avatar.getStyle().set("background", bg);
        if (fg != null) avatar.getStyle().set("color", fg);
        if (tooltip != null) avatar.getElement().setAttribute("title", tooltip);
        LkPopover pop = new LkPopover(avatar, menu).position(PopoverPosition.BOTTOM_END);
        rightSlot.add(pop);
        return this;
    }

    /** "Sign in" link + "Register" ghost button for guest sessions. */
    public LkTopBar guestActions(Class<? extends Component> signInTarget,
                                 Class<? extends Component> registerTarget) {
        ensureRightSlot();
        RouterLink signIn = new RouterLink("Sign in", signInTarget);
        signIn.addClassName("lk-topnav-link");
        signIn.addClassName("on");
        signIn.getStyle().set("opacity", ".92");
        RouterLink reg = new RouterLink("Register", registerTarget);
        reg.addClassName("lk-btn");
        reg.addClassName(LkBtn.GHOST_LIGHT);
        reg.addClassName("lk-btn-s");
        rightSlot.add(signIn, reg);
        return this;
    }

    public Variant getVariant() { return variant; }

    private void ensureRightSlot() {
        if (rightAttached) return;
        add(rightSlot);
        rightAttached = true;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
