package com.ticketing.system.Presentation.views.account;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkField;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.session.AuthSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "my-profile", layout = MainLayout.class)
@PageTitle("My profile · TicketHub")
@PermitAll
public class MyProfileView extends LkPage {

    public MyProfileView() {
        title("My profile");
        subtitle("Your account details.");
        add(buildSplit());
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("prof-split");
        split.add(buildProfileCard(), buildSideCol());
        return split;
    }

    private Component buildProfileCard() {
        String name = AuthSession.displayName();
        if (name == null || name.isBlank()) name = "Alex Morgan";

        LkCard card = new LkCard("Profile").pad(20);

        Div idRow = new Div();
        idRow.addClassName("prof-id");
        Span avatar = new Span(initials(name));
        avatar.addClassName("prof-av");
        Div info = new Div();
        Div nameDiv = new Div();
        nameDiv.addClassName("prof-name");
        nameDiv.setText(name);
        info.add(nameDiv, new LkBadge("Verified", LkBadge.Tone.success).small());
        idRow.add(avatar, info);

        LkCol fields = new LkCol().gap(14);
        fields.add(new LkField().label("Username").value("alex.morgan"));
        fields.add(new LkField().label("Email").value("alex.morgan@email.com"));
        fields.add(new LkField().label("Member since").value("15 Dec 2024"));

        LkRow actions = new LkRow().gap(8);
        actions.getStyle().set("margin-top", "16px");
        actions.add(
            new LkBtn("Edit profile").variant(LkBtn.Variant.secondary)
                .onClick(e -> Toasts.warn("Profile editing is exempt in Tier C (II.3.4).")),
            new LkBtn("Change password").variant(LkBtn.Variant.tertiary)
                .onClick(e -> Toasts.warn("Password change wires with V2-AUTH-02."))
        );

        card.add(idRow, Lk.divider(), fields, actions);
        return card;
    }

    private Component buildSideCol() {
        LkCol col = new LkCol().gap(14);

        LkCard prefs = new LkCard("Preferences").pad(16);
        LkCol p = new LkCol().gap(8);
        Span pDesc = Lk.muted("Notifications, language, and privacy settings.");
        pDesc.getStyle().set("font-size", "13.5px");
        p.add(pDesc);
        p.add(new LkBtn("Manage preferences").variant(LkBtn.Variant.secondary).full()
            .onClick(e -> Toasts.warn("Preferences screen — wired by V2-MEM-04.")));
        prefs.add(p);

        LkCard sessions = new LkCard("Sessions").pad(16);
        LkCol s = new LkCol().gap(8);
        Span sDesc = new Span("Signed in on 2 devices.");
        sDesc.getStyle().set("font-size", "13.5px");
        s.add(sDesc);
        s.add(new LkBtn("Sign out everywhere").variant(LkBtn.Variant.tertiary).full()
            .onClick(e -> Toasts.warn("Multi-device session control — V2-AUTH-02.")));
        sessions.add(s);

        col.add(prefs, sessions);
        return col;
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "??";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            String p = parts[0];
            return p.substring(0, Math.min(2, p.length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}
