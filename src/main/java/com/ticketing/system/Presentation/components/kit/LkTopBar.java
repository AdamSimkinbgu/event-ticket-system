package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

/**
 * Top app bar — buyer, organizer-admin, or platform-admin variants.
 * Ports the React {@code TopBar}.
 *
 * <ul>
 *   <li>{@link Variant#MAIN} — buyer shell. Brand · nav · search · bell · avatar (or sign-in buttons if guest).</li>
 *   <li>{@link Variant#ADMIN} — organizer workspace. Brand " · Workspace" crumb, same right side.</li>
 *   <li>{@link Variant#PLATFORM} — system admin. Orange gradient, "Back to site" link, admin bell + avatar.</li>
 * </ul>
 */
public class LkTopBar extends Header {

    public enum Variant { MAIN, ADMIN, PLATFORM }

    public record NavItem(String label, Class<? extends Component> target) { }

    private final Variant variant;

    public LkTopBar(Variant variant, String brandText, List<NavItem> navItems, String activeNavLabel,
                    boolean signedIn, String avatarInitials, String avatarName, String avatarEmail) {
        this.variant = variant;
        addClassName("lk-topbar");
        if (variant == Variant.PLATFORM) addClassName("lk-topbar-admin");

        // Brand
        Span brand = new Span();
        brand.addClassName("lk-brand");
        brand.add(new LkIcon("ticket", 20));
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(brandText) + "</b>");
        brand.add(name);
        if (variant == Variant.ADMIN) {
            Span crumb = new Span(" › Workspace");
            crumb.addClassName("lk-brand-crumb");
            brand.add(crumb);
        } else if (variant == Variant.PLATFORM) {
            Span crumb = new Span(" · Admin");
            crumb.addClassName("lk-brand-crumb");
            brand.add(crumb);
        }
        add(brand);

        if (variant == Variant.PLATFORM) {
            Span spacer = new Span();
            spacer.getStyle().set("flex", "1");
            add(spacer);
            Span back = new Span();
            back.addClassName("lk-topnav-link");
            back.addClassName("on");
            back.getStyle().set("opacity", ".92").set("display", "inline-flex").set("align-items", "center").set("gap", "5px");
            back.add(new LkIcon("arrowLeft", 14), new Span("Back to site"));
            add(back);
            Span right = buildRightSlot(signedIn, avatarInitials, avatarName, avatarEmail);
            add(right);
            return;
        }

        // Main + Admin variants share the structure
        Span nav = new Span();
        nav.addClassName("lk-topnav");
        if (navItems != null) {
            for (NavItem item : navItems) {
                if (item.target != null) {
                    RouterLink link = new RouterLink(item.label, item.target);
                    link.addClassName("lk-topnav-link");
                    if (item.label.equals(activeNavLabel)) link.addClassName("on");
                    nav.add(link);
                } else {
                    Span a = new Span(item.label);
                    a.addClassName("lk-topnav-link");
                    if (item.label.equals(activeNavLabel)) a.addClassName("on");
                    nav.add(a);
                }
            }
        }
        add(nav);

        // Search slot — uses the search popover
        Span searchTrigger = new Span();
        searchTrigger.addClassName("lk-topbar-search");
        searchTrigger.add(new LkIcon("search", 16));
        Span searchTxt = new Span("Search events, artists, venues…");
        searchTxt.addClassName("lk-search-txt");
        searchTrigger.add(searchTxt);

        LkPopover searchPop = new LkPopover(searchTrigger, LkSearchPanel.defaults())
            .position(PopoverPosition.BOTTOM_END)
            .width("392px");
        searchPop.addClassName("lk-search-wrap");
        add(searchPop);

        add(buildRightSlot(signedIn, avatarInitials, avatarName, avatarEmail));
    }

    private Span buildRightSlot(boolean signedIn, String avatarInitials, String avatarName, String avatarEmail) {
        Span right = new Span();
        right.addClassName("lk-topbar-right");

        // Bell
        NativeButton bell = new NativeButton();
        bell.addClassName("lk-bell");
        bell.getElement().setAttribute("aria-label", variant == Variant.PLATFORM ? "Admin alerts" : "Notifications");
        bell.add(new LkIcon("bell", 18));
        Span badge = new Span(variant == Variant.PLATFORM ? "5" : "3");
        badge.addClassName("lk-bell-badge");
        bell.add(badge);
        LkPopover bellPop = new LkPopover(bell, variant == Variant.PLATFORM ? LkNotifPanel.admin() : LkNotifPanel.buyer())
            .position(PopoverPosition.BOTTOM_END);
        right.add(bellPop);

        // Avatar OR Sign in / Register for guest
        if (signedIn || variant != Variant.MAIN) {
            NativeButton avatar = new NativeButton(avatarInitials == null ? "AM" : avatarInitials);
            avatar.addClassName("lk-avatar");
            if (variant == Variant.PLATFORM) avatar.getStyle().set("background", "#fff").set("color", "#c2410c");
            avatar.getElement().setAttribute("title", avatarName == null ? "" : avatarName);
            LkAccountMenu menu = variant == Variant.PLATFORM
                ? LkAccountMenu.admin(avatarInitials == null ? "AD" : avatarInitials, avatarName == null ? "Admin" : avatarName)
                : LkAccountMenu.buyer(avatarInitials == null ? "AM" : avatarInitials,
                                      avatarName == null ? "Alex Morgan" : avatarName,
                                      avatarEmail == null ? "alex.morgan@email.com" : avatarEmail);
            LkPopover avatarPop = new LkPopover(avatar, menu).position(PopoverPosition.BOTTOM_END);
            right.add(avatarPop);
        } else {
            Span signIn = new Span("Sign in");
            signIn.addClassName("lk-topnav-link");
            signIn.addClassName("on");
            signIn.getStyle().set("opacity", ".92");
            NativeButton reg = new NativeButton("Register");
            reg.addClassName("lk-btn");
            reg.addClassName(LkBtn.GHOST_LIGHT);
            reg.addClassName("lk-btn-s");
            right.add(signIn, reg);
        }
        return right;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
